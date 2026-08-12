package com.garagepi.telemetry.obd

/**
 * Ioniq 5 Mode 22 (UDS) decoders — Kotlin port of garagepi's
 * `ioniq_mode22.py`, which validated these byte offsets (Torque Pro CSV
 * letters) against a live Ioniq 5. `data` here is the UDS payload with the
 * `62 <did_hi> <did_lo>` response header already stripped (see
 * [UdsResponseParser.parseData]) — same shape garagepi's `uds_data()` hands
 * its decoders.
 */
object IoniqUds {
    data class UdsQuery(val header: String, val requestHex: String, val decode: (ByteArray) -> Map<String, Double>)

    private fun u8(d: ByteArray, i: Int): Int = d[i].toInt() and 0xFF
    private fun s8(d: ByteArray, i: Int): Int {
        val v = u8(d, i)
        return if (v > 127) v - 256 else v
    }
    private fun u16(d: ByteArray, hi: Int, lo: Int): Int = (u8(d, hi) shl 8) or u8(d, lo)
    private fun s16(d: ByteArray, hi: Int, lo: Int): Int {
        val v = u16(d, hi, lo)
        return if (v > 32767) v - 65536 else v
    }

    /** Torque Pro letter index: a=0, b=1, ... z=25, aa=26, ab=27, ... */
    private fun idx(letters: String): Int {
        val l = letters.lowercase()
        return if (l.length == 1) l[0] - 'a' else 26 * (l[0] - 'a' + 1) + (l[1] - 'a')
    }

    private val E = idx("e")
    private val K = idx("k")
    private val L = idx("l")
    private val M = idx("m")
    private val N = idx("n")
    private val O = idx("o")
    private val P = idx("p")
    private val AD = idx("ad")
    private val AF = idx("af")
    private val Z = idx("z")
    private val AA = idx("aa")
    private val W = idx("w")

    private fun round1(v: Double) = kotlin.math.round(v * 10) / 10.0
    private fun round2(v: Double) = kotlin.math.round(v * 100) / 100.0

    /** BMS snapshot (header 7E4): SOC, current, voltage, power, temps. */
    fun decode220101(data: ByteArray): Map<String, Double> {
        if (data.size < 20) return emptyMap()
        val hvSoc = u8(data, E) / 2.0
        val currentA = s16(data, K, L) / 10.0
        val packV = u16(data, M, N) / 10.0
        val out = mutableMapOf(
            "HV_SOC" to round1(hvSoc),
            "PACK_CURRENT_A" to round2(currentA),
            "PACK_VOLTAGE_V" to round1(packV),
            "PACK_POWER_KW" to round2(currentA * packV / 1000.0),
            "BATT_TEMP_MAX_C" to s8(data, O).toDouble(),
            "BATT_TEMP_MIN_C" to s8(data, P).toDouble(),
        )
        if (data.size > AD) out["AUX_VOLTAGE_V"] = round2(u8(data, AD) * 0.1)
        return out
    }

    /**
     * Extended BMS (header 7E4): SOH and dashboard-display SOC.
     *
     * Torque CSV letters index the stripped payload directly — there is no -3 shift for the
     * `62 xx xx` header. Evidence, all against a live Long Range (77.4 kWh) Ioniq 5:
     *  - SOH (`z`/`aa`, unshifted) = 91.7%, plausible.
     *  - Display SOC (`af` = data[31]) = 54.5% against a dash reading 54 (display SOC is
     *    truncated, and raw BMS SOC from 220101 was 55.0%). data[31] is also the offset the
     *    widely-used Kona/Ioniq 2105 spec uses.
     *  - garagepi's ioniq_mode22.py assumed the shifted convention and read this region as
     *    remaining energy instead; that decodes to 33.2 kWh at 54% SOC, implying a ~61 kWh
     *    pack, which is wrong for this car — so the shifted convention is falsified.
     *
     * [TelemetryFields.HV_SOC] still drives the dashboard from 220101, since both this app
     * and garagepi emit that field and agree on it.
     */
    fun decode220105(data: ByteArray): Map<String, Double> {
        val out = mutableMapOf<String, Double>()
        if (data.size > AA) out["HV_SOH"] = round1(u16(data, Z, AA) / 10.0)
        if (data.size > AF) out["HV_SOC_DISPLAY"] = round1(u8(data, AF) / 2.0)
        return out
    }

    /** ICCU aux 12V SOC (header 7E5). */
    fun decode22E011(data: ByteArray): Map<String, Double> {
        if (data.size < 23 || data.size <= W) return emptyMap()
        return mapOf("AUX_SOC" to u8(data, W).toDouble())
    }

    val QUERIES: List<UdsQuery> = listOf(
        UdsQuery("7E4", "220101", ::decode220101),
        UdsQuery("7E4", "220105", ::decode220105),
        UdsQuery("7E5", "22E011", ::decode22E011),
    )
}
