package com.garagepi.telemetry.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.garagepi.telemetry.MainActivity
import com.garagepi.telemetry.R
import com.garagepi.telemetry.bluetooth.Elm327Connection
import com.garagepi.telemetry.data.ReadingEntity
import com.garagepi.telemetry.data.TelemetryDatabase
import com.garagepi.telemetry.data.TripSessionEntity
import com.garagepi.telemetry.obd.ObdSession
import com.garagepi.telemetry.obd.TelemetryFields
import com.garagepi.telemetry.sync.AppSettings
import com.garagepi.telemetry.sync.SyncScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "ObdLoggingService"
private const val CHANNEL_ID = "obd_logging"
private const val NOTIFICATION_ID = 1

/**
 * Gap between poll cycles. Kept short deliberately: with the slow queries staggered, a
 * typical cycle is a single 220101 round trip (~0.4 s), so a long idle here is the main
 * thing limiting how granular the history charts can be.
 */
private const val POLL_INTERVAL_MS = 200L

/**
 * Consecutive empty polls treated as "the car powered off".
 *
 * The adapter does not reliably drop the Bluetooth socket when the car shuts down — it
 * can simply stop answering, in which case `sendCommand` times out and returns an empty
 * string rather than throwing. Without this counter the loop spins forever and the
 * session never gets an `endedAt`.
 */
private const val EMPTY_POLLS_BEFORE_STOP = 5

/**
 * Runs the OBD connection and poll loop as a foreground service so a drive keeps
 * recording with the screen off or the app backgrounded. This previously lived in
 * `viewModelScope`, which Android is free to kill mid-drive.
 */
class ObdLoggingService : Service() {

    private val scope = CoroutineScope(SupervisorJob())
    private var loggingJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val db by lazy { TelemetryDatabase.get(applicationContext) }
    private val settings by lazy { AppSettings(applicationContext) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val address = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
                if (address.isNullOrBlank()) {
                    Log.w(TAG, "start requested without a device address")
                    stopSelfCleanly()
                } else {
                    startLogging(address)
                }
            }
            ACTION_STOP -> stopLogging()
            else -> {
                Log.w(TAG, "unknown action ${intent?.action}")
                stopSelfCleanly()
            }
        }
        // Not sticky: a restart with a null intent could not reconnect meaningfully, and
        // silently resuming would open a second session for the same drive.
        return START_NOT_STICKY
    }

    private fun startLogging(address: String) {
        if (loggingJob?.isActive == true) {
            Log.i(TAG, "already logging; ignoring duplicate start")
            return
        }

        startForegroundCompat(buildNotification("Connecting…", null))
        acquireWakeLock()
        ObdLoggingState.setConnection(ConnectionState.Connecting)

        loggingJob = scope.launch { runLoggingSession(address) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun runLoggingSession(address: String) {
        var connection: Elm327Connection? = null
        var obdSession: ObdSession? = null
        var tripId: Long? = null

        try {
            val adapter = bluetoothAdapter() ?: throw IllegalStateException("Bluetooth unavailable")
            val device = adapter.getRemoteDevice(address)

            connection = Elm327Connection.connect(device, adapter)
            val session = ObdSession(connection, settings.calibratedFields())
            session.initialize()
            obdSession = session

            tripId = db.tripSessionDao().insert(TripSessionEntity(startedAt = System.currentTimeMillis()))
            settings.lastDeviceAddress = address

            val label = device.name ?: device.address
            ObdLoggingState.setConnection(ConnectionState.Connected(label))
            updateNotification("Logging from $label", null)

            pollUntilPowerOff(session, tripId, label)
            ObdLoggingState.setConnection(ConnectionState.Disconnected)
        } catch (e: CancellationException) {
            // User pressed Stop. Not an error state — fall through to cleanup.
            ObdLoggingState.setConnection(ConnectionState.Disconnected)
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "logging session failed: ${e.message}")
            ObdLoggingState.setConnection(ConnectionState.Error(e.message ?: "Connection failed"))
        } finally {
            // NonCancellable is essential: when the job is cancelled (Stop pressed), any
            // suspend call in an ordinary finally block throws immediately, so endedAt
            // would never be written and the session would be orphaned.
            withContext(NonCancellable) {
                obdSession?.close()
                connection?.close()
                endTrip(tripId)
                ObdLoggingState.clearValues()
            }
            stopSelfCleanly()
        }
    }

    private suspend fun pollUntilPowerOff(session: ObdSession, tripId: Long, label: String) {
        var emptyPolls = 0

        while (currentCoroutineContext().isActive) {
            val result = session.pollOnce()
            val readings = result.readings
            ObdLoggingState.mergeCalibrationFrames(result.calibrationFrames)

            if (readings.isEmpty()) {
                emptyPolls++
                if (emptyPolls >= EMPTY_POLLS_BEFORE_STOP) {
                    Log.i(TAG, "no data for $emptyPolls consecutive polls — treating as power off")
                    return
                }
            } else {
                emptyPolls = 0
                val values = readings.associate { it.pid to it.value }
                ObdLoggingState.mergeValues(values)
                db.readingDao().insertAll(
                    readings.map {
                        ReadingEntity(
                            tripSessionId = tripId,
                            ts = it.timestampMs,
                            pid = it.pid,
                            value = it.value,
                        )
                    },
                )
                updateNotification("Logging from $label", values)
            }

            delay(POLL_INTERVAL_MS)
        }
    }

    /** Stamps endedAt so the session is a complete drive, then nudges the uploader. */
    private suspend fun endTrip(tripId: Long?) {
        if (tripId == null) return
        db.tripSessionDao().getById(tripId)?.let { trip ->
            if (trip.endedAt == null) {
                db.tripSessionDao().update(trip.copy(endedAt = System.currentTimeMillis()))
            }
        }
        SyncScheduler.triggerNow(applicationContext)
    }

    private fun stopLogging() {
        val job = loggingJob
        if (job == null) {
            stopSelfCleanly()
            return
        }
        // Cancel off the main thread and let the NonCancellable cleanup above finish
        // writing endedAt; blocking here would risk an ANR.
        scope.launch {
            job.cancelAndJoin()
            loggingJob = null
        }
    }

    private fun stopSelfCleanly() {
        releaseWakeLock()
        stopForegroundCompat()
        stopSelf()
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun bluetoothAdapter(): BluetoothAdapter? =
        (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "garage-telemetry:logging").apply {
            setReferenceCounted(false)
            acquire(MAX_WAKELOCK_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "OBD logging",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Shown while a drive is being recorded" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(title: String, values: Map<String, Double>?): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, ObdLoggingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(summarize(values))
            .setSmallIcon(R.drawable.ic_stat_logging)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun summarize(values: Map<String, Double>?): String {
        if (values.isNullOrEmpty()) return "Waiting for data…"
        val parts = listOfNotNull(
            values[TelemetryFields.HV_SOC.pid]?.let { "SOC %.0f%%".format(it) },
            values[TelemetryFields.SPEED.pid]?.let { "%.0f km/h".format(it) },
            values[TelemetryFields.PACK_POWER.pid]?.let { "%.1f kW".format(it) },
        )
        return if (parts.isEmpty()) "Recording…" else parts.joinToString("  ·  ")
    }

    private fun updateNotification(title: String, values: Map<String, Double>?) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(title, values))
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    companion object {
        const val ACTION_START = "com.garagepi.telemetry.action.START_LOGGING"
        const val ACTION_STOP = "com.garagepi.telemetry.action.STOP_LOGGING"
        const val EXTRA_DEVICE_ADDRESS = "device_address"

        /** Ceiling only; the lock is released in the cleanup path regardless. */
        private const val MAX_WAKELOCK_MS = 6 * 60 * 60 * 1000L

        fun start(context: Context, deviceAddress: String) {
            val intent = Intent(context, ObdLoggingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_DEVICE_ADDRESS, deviceAddress)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, ObdLoggingService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
