package com.garagepi.telemetry.obd

private val HEX_BYTE = Regex("^[0-9A-F]{1,2}$")

/** Splits a raw ELM327 line into byte tokens, dropping anything that isn't exactly a hex byte
 *  (rejects "NO DATA", "?", stray prompt characters, etc. rather than misreading them as hex). */
private fun hexTokens(raw: String): List<String> =
    raw.uppercase().split(Regex("\\s+")).filter { it.matches(HEX_BYTE) }

/** Parses a Mode 01 response for PID 0D (speed) — the one standard PID that still applies to an EV. */
object ObdResponseParser {
    private const val SPEED_PID_BYTE = "0D"

    fun decodeSpeed(raw: String): Double? {
        val tokens = hexTokens(raw)
        val modeIndex = tokens.indexOf("41")
        if (modeIndex < 0 || modeIndex + 2 >= tokens.size) return null
        if (tokens[modeIndex + 1] != SPEED_PID_BYTE) return null
        return tokens[modeIndex + 2].toIntOrNull(16)?.toDouble()
    }
}

/** Parses a Mode 22 (UDS) response into the payload after the `62 <did_hi> <did_lo>` echo. */
object UdsResponseParser {
    fun parseData(raw: String): ByteArray? {
        val tokens = hexTokens(raw)
        if (tokens.size < 4) return null
        val bytes = tokens.mapNotNull { it.toIntOrNull(16) }
        if (bytes.isEmpty() || bytes[0] != 0x62) return null
        return bytes.drop(3).map { it.toByte() }.toByteArray()
    }
}
