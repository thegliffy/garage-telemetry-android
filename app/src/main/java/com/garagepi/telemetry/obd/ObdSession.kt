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
 * Runs the ELM327 init sequence, then polls Speed (Mode 01, works on any vehicle including EVs)
 * plus the Ioniq 5's Mode 22 EV telemetry (HV SOC, pack voltage/power, battery temp, 12V aux SOC)
 * on demand. Each Mode 22 query needs its own `AT SH <header>` before the request, since the ECU
 * that answers 220101/220105 (BMS, 7E4) differs from 22E011 (ICCU, 7E5).
 */
class ObdSession(private val connection: Elm327Connection) {

    suspend fun initialize() {
        for (command in ELM327_INIT_SEQUENCE) {
            connection.sendCommand(command)
        }
    }

    suspend fun pollOnce(): List<PidReading> {
        val now = System.currentTimeMillis()
        val readings = mutableListOf<PidReading>()

        connection.sendCommand("AT SH 7DF")
        ObdResponseParser.decodeSpeed(connection.sendCommand("010D"))
            ?.let { readings.add(PidReading(TelemetryFields.SPEED.pid, it, now)) }

        for (query in IoniqUds.QUERIES) {
            connection.sendCommand("AT SH ${query.header}")
            val raw = connection.sendCommand(query.requestHex, timeoutMs = 8_000)
            val data = UdsResponseParser.parseData(raw)
            if (data == null) {
                Log.w(TAG, "${query.requestHex}: unparseable UDS response '$raw'")
                continue
            }
            val decoded = query.decode(data)
            if (decoded.isEmpty()) {
                Log.w(TAG, "${query.requestHex}: payload too short (${data.size}B) = ${data.toHex()}")
            }
            for ((pid, value) in decoded) {
                readings.add(PidReading(pid, value, now))
            }
        }

        return readings
    }

    fun close() = connection.close()
}

private fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }
