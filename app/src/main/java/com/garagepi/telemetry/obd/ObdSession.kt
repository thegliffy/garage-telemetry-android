package com.garagepi.telemetry.obd

import android.util.Log
import com.garagepi.telemetry.bluetooth.Elm327Connection

private const val TAG = "ObdSession"

/** ELM327 setup: reset, echo off, linefeeds off, headers off, CAN auto-formatting on, auto protocol. */
val ELM327_INIT_SEQUENCE: List<String> = listOf("ATZ", "ATE0", "ATL0", "ATH0", "ATCAF1", "ATSP0")

/** One decoded value from a single poll cycle. `pid` doubles as the shared reading identifier
 *  (see [TelemetryField]) — a standard hex PID for Speed, or a garagepi-matching field name
 *  (e.g. "HV_SOC_DISPLAY") for the Ioniq Mode 22 values. */
data class PidReading(val pid: String, val value: Double, val timestampMs: Long)

/**
 * @param calibrationFrames latest raw payload per not-yet-decoded PID, keyed by request
 *   hex. Surfaced so the in-app calibration screen can scan real frames from the car
 *   without needing a laptop tethered to the phone.
 */
data class PollResult(
    val readings: List<PidReading> = emptyList(),
    val calibrationFrames: Map<String, ByteArray> = emptyMap(),
) {
    val isEmpty: Boolean get() = readings.isEmpty()
}

/**
 * Runs the ELM327 init sequence, then polls Speed (Mode 01, works on any vehicle including EVs)
 * plus the Ioniq 5's Mode 22 EV telemetry (HV SOC, pack voltage/power, battery temp, 12V aux SOC)
 * on demand. Each Mode 22 query needs its own `AT SH <header>` before the request, since the ECU
 * that answers 220101/220105 (BMS, 7E4) differs from 22E011 (ICCU, 7E5).
 */
class ObdSession(
    private val connection: Elm327Connection,
    /** Saved calibrations, keyed by request hex — turns a calibrating query into real readings. */
    private val calibrations: Map<String, CalibratedField> = emptyMap(),
) {

    suspend fun initialize() {
        for (command in ELM327_INIT_SEQUENCE) {
            connection.sendCommand(command)
        }
        // ATZ resets the adapter, so any header we thought was set is gone.
        currentHeader = null
        cycle = 0
    }

    private var cycle = 0L
    private var currentHeader: String? = null

    suspend fun pollOnce(): PollResult {
        val now = System.currentTimeMillis()
        val readings = mutableListOf<PidReading>()
        val frames = mutableMapOf<String, ByteArray>()
        val thisCycle = cycle++

        // Standard Mode 01 speed (010D) is not polled: this car answers NO DATA for it,
        // so it only cost a round trip per cycle. Speed comes from the VMCU instead.
        for (query in IoniqUds.QUERIES) {
            if (thisCycle % query.everyNCycles != 0L) continue

            // 220101 and 220105 share header 7E4; re-sending AT SH between them is a
            // wasted round trip on a link where every round trip costs ~200 ms.
            if (currentHeader != query.header) {
                connection.sendCommand("AT SH ${query.header}")
                currentHeader = query.header
            }
            val raw = connection.sendCommand(query.requestHex, timeoutMs = 8_000)
            val data = UdsResponseParser.parseData(raw)
            if (data == null) {
                Log.w(TAG, "${query.requestHex}: unparseable UDS response '$raw'")
                continue
            }
            // Publish every frame, not just uncalibrated ones, so the calibration screen
            // can re-derive an offset for a field that turns out to be decoded wrongly.
            frames[query.requestHex] = data

            val override = calibrations[query.requestHex]
            val decoded = when {
                // A calibration saved in-app beats the built-in offset, so a bad decode
                // can be corrected in the car without waiting for a new build.
                override != null ->
                    override.spec.extract(data)?.let { mapOf(override.pid to it) }.orEmpty()
                query.calibrating -> emptyMap()
                else -> query.decode(data)
            }
            if (decoded.isEmpty() && !query.calibrating) {
                Log.w(TAG, "${query.requestHex}: payload too short (${data.size}B) = ${data.toHex()}")
            }
            for ((pid, value) in decoded) {
                readings.add(PidReading(pid, value, now))
            }
        }

        return PollResult(readings, frames)
    }

    fun close() = connection.close()
}

/** A calibration the user confirmed in-app: which bytes to read and what to call the result. */
data class CalibratedField(val pid: String, val spec: CandidateSpec)

private fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }
