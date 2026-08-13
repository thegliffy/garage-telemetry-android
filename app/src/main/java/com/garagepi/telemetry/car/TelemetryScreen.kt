package com.garagepi.telemetry.car

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.garagepi.telemetry.obd.TelemetryField
import com.garagepi.telemetry.obd.TelemetryFields
import com.garagepi.telemetry.service.ConnectionState
import com.garagepi.telemetry.service.LoggingState
import com.garagepi.telemetry.service.ObdLoggingService
import com.garagepi.telemetry.service.ObdLoggingState
import com.garagepi.telemetry.sync.AppSettings
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Minimum gap between template refreshes.
 *
 * The car host throttles refreshes anyway, and redrawing at the ~1.8 Hz sample rate would
 * just burn battery producing frames the host discards.
 */
private const val REFRESH_INTERVAL_MS = 1_000L

class TelemetryScreen(carContext: CarContext) : Screen(carContext) {

    private val settings = AppSettings(carContext)
    private var lastRefresh = 0L

    init {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ObdLoggingState.state.collect {
                    val now = System.currentTimeMillis()
                    if (now - lastRefresh >= REFRESH_INTERVAL_MS) {
                        lastRefresh = now
                        invalidate()
                    }
                }
            }
        }
    }

    override fun onGetTemplate(): Template {
        val state = ObdLoggingState.state.value
        return when (state.connectionState) {
            is ConnectionState.Connected -> paneTemplate(state)
            ConnectionState.Connecting -> message("Connecting to the adapter…", showStart = false)
            is ConnectionState.Error ->
                message("Connection failed: ${state.connectionState.message}", showStart = true)
            ConnectionState.Disconnected -> message("Not logging.", showStart = true)
        }
    }

    private fun paneTemplate(state: LoggingState): Template {
        val pane = Pane.Builder()
        val rows = TelemetryFields.CAR_FIELDS.mapNotNull { field ->
            state.latestValues[field.pid]?.let { row(field, it) }
        }

        if (rows.isEmpty()) {
            // A Pane must have content or be explicitly loading, otherwise the host rejects it.
            pane.setLoading(true)
        } else {
            rows.forEach(pane::addRow)
            pane.addAction(
                Action.Builder()
                    .setTitle("Stop")
                    .setOnClickListener {
                        ObdLoggingService.stop(carContext)
                        CarToast.makeText(carContext, "Logging stopped", CarToast.LENGTH_SHORT).show()
                    }
                    .build(),
            )
        }

        return PaneTemplate.Builder(pane.build())
            .setTitle("Ioniq 5")
            .setHeaderAction(Action.APP_ICON)
            .build()
    }

    private fun row(field: TelemetryField, value: Double): Row {
        // Templates give no reliable color control per row, so direction is spelled out
        // in words rather than relying on the red/green treatment the phone UI uses.
        val decimals = field.decimals.coerceIn(0, 3)
        val text = when {
            field.isBoolean -> if (value != 0.0) "Yes" else "No"
            field.signedFlow -> {
                val direction = if (value > 0) "discharging" else if (value < 0) "charging" else "idle"
                "%.${decimals}f %s · %s".format(abs(value), field.unit, direction)
            }
            else -> "%.${decimals}f %s".format(value, field.unit)
        }
        return Row.Builder().setTitle(field.label).addText(text).build()
    }

    private fun message(text: String, showStart: Boolean): Template {
        val lastDevice = settings.lastDeviceAddress
        // MessageTemplate takes its body up front, so the hint is folded into the text
        // rather than added afterwards.
        val body = if (showStart && lastDevice == null) {
            "$text\nConnect to the adapter once from the phone first."
        } else {
            text
        }

        val builder = MessageTemplate.Builder(body)
            .setTitle("Ioniq 5")
            .setHeaderAction(Action.APP_ICON)

        if (showStart && lastDevice != null) {
            // Reconnects to the adapter used last, so nothing has to be picked from a
            // list while driving — which the distraction guidelines rule out anyway.
            builder.addAction(
                Action.Builder()
                    .setTitle("Start logging")
                    .setBackgroundColor(CarColor.GREEN)
                    .setOnClickListener {
                        ObdLoggingService.start(carContext, lastDevice)
                        CarToast.makeText(carContext, "Connecting…", CarToast.LENGTH_SHORT).show()
                    }
                    .build(),
            )
        }
        return builder.build()
    }
}
