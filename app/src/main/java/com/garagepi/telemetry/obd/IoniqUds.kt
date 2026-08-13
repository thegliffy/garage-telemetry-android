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
    /**
     * @param calibrating true for queries we poll only to capture raw frames in logcat,
     *   because the byte offsets are not derived yet. They contribute no readings.
     * @param everyNCycles poll divisor. Each query costs a round trip (~0.4 s), so polling
     *   slow-moving values (SOH, aux SOC, odometer) on every cycle throttles the sample
     *   rate of the fast ones — voltage, current, power — which is what makes the history
     *   charts look coarse.
     */
    data class UdsQuery(
        val header: String,
        val requestHex: String,
        val decode: (ByteArray) -> Map<String, Double>,
        val calibrating: Boolean = false,
        val everyNCycles: Int = 1,
    )

    private fun u8(d: ByteArray, i: Int): Int = d[i].toInt() and 0xFF
    private fun s8(d: ByteArray, i: Int): Int {
        val v = u8(d, i)
        return if (v > 127) v - 256 else v
    }
    private fun u16(d: ByteArray, hi: Int, lo: Int): Int = (u8(d, hi) shl 8) or u8(d, lo)
    private fun u32(d: ByteArray, start: Int): Long {
        var v = 0L
        for (i in 0 until 4) v = (v shl 8) or (d[start + i].toLong() and 0xFF)
        return v
    }
    private fun bit(d: ByteArray, i: Int, b: Int): Double = if (u8(d, i) and (1 shl b) != 0) 1.0 else 0.0
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

    /**
     * BMS snapshot (header 7E4): SOC, current, voltage, power, temps.
     *
     * Sign convention: **positive = discharge** (power leaving the battery),
     * **negative = charge/regen**. The dashboard shows the magnitude and conveys
     * direction with color rather than a minus sign.
     *
     * NOT negated, despite a report that power reads inverted. The one capture we have
     * with a known vehicle state — parked in READY, so the pack must be discharging to
     * run the DC-DC and electronics — decodes to +1.2 A / +0.86 kW, which is already
     * discharge-positive. Flipping it would make that known-good case read as charging.
     * Confirm on the road instead: braking/regen and plugged-in charging must both show
     * green, acceleration red. If they do not, negate here and in decode_capture.py.
     */
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

        // Everything below rides on a frame already being fetched, so it costs no extra
        // round trip. Offsets from the Esprit1st Ioniq 5 Torque CSV, cross-checked against
        // a captured frame: 192 cells x 3.71 V avg = 712 V against a measured 714.7 V pack.
        if (data.size > idx("aa")) {
            out["CELL_V_MAX"] = round2(u8(data, idx("x")) / 50.0)
            out["CELL_V_MIN"] = round2(u8(data, idx("z")) / 50.0)
        }
        if (data.size > 9) {
            out["HV_CHARGING"] = bit(data, 9, 7)
            out["AC_PLUG"] = bit(data, 9, 5)
            out["CCS_PLUG"] = bit(data, 9, 6)
        }
        if (data.size > idx("ba")) out["INVERTER_CAP_V"] = u16(data, idx("az"), idx("ba")).toDouble()
        if (data.size > idx("bg")) out["ISOLATION_KOHM"] = u16(data, idx("bf"), idx("bg")).toDouble()
        if (data.size > idx("bc")) out["MOTOR_RPM_REAR"] = s16(data, idx("bb"), idx("bc")).toDouble()
        if (data.size > idx("be")) out["MOTOR_RPM_FRONT"] = s16(data, idx("bd"), idx("be")).toDouble()
        if (data.size > idx("ap")) out["CEC_KWH"] = round1(u32(data, idx("am")) / 10.0)
        if (data.size > idx("at")) out["CED_KWH"] = round1(u32(data, idx("aq")) / 10.0)
        if (data.size > idx("ax")) out["OPTIME_H"] = round1(u32(data, idx("au")) / 3600.0)
        return out
    }

    /**
     * Extended BMS (header 7E4): SOH, display SOC, remaining energy and power limits.
     *
     * Torque CSV letters index the stripped payload directly — there is no -3 shift for the
     * `62 xx xx` header. Verified against the Esprit1st Ioniq 5 Torque list and a live
     * Long Range car:
     *  - SOH (`z`/`aa`) = 91.7%.
     *  - Display SOC (`af` = data[31]) = 54.5% against a dash reading 54, and holds a
     *    steady +1.99% offset from raw BMS SOC (stdev 0.17) across a session. It was
     *    briefly removed on the strength of an apparent -23%..+2% wander, but that was a
     *    measurement error: those readings pooled sessions recorded by two *different*
     *    decoder versions.
     *  - Remaining energy (`ac`/`ad`) is a separate field in the same region, which is why
     *    both readings of these bytes looked defensible at once. The CSV confirms both.
     *    Its 33.2 kWh at 54% implies a ~61 kWh pack though, so treat the value as
     *    unverified until compared against the car's own range estimate.
     *
     * [TelemetryFields.HV_SOC] still drives the dashboard from 220101, since both this app
     * and garagepi emit that field and agree on it.
     */
    fun decode220105(data: ByteArray): Map<String, Double> {
        val out = mutableMapOf<String, Double>()
        if (data.size > AA) out["HV_SOH"] = round1(u16(data, Z, AA) / 10.0)
        if (data.size > AF) out["HV_SOC_DISPLAY"] = round1(u8(data, AF) / 2.0)
        if (data.size > idx("ad")) {
            out["REMAINING_ENERGY_KWH"] = round2(u16(data, idx("ac"), idx("ad")) * 2 / 1000.0)
        }
        if (data.size > idx("t")) {
            out["MAX_REGEN_KW"] = round1(u16(data, idx("q"), idx("r")) / 100.0)
            out["MAX_POWER_KW"] = round1(u16(data, idx("s"), idx("t")) / 100.0)
        }
        if (data.size > idx("x")) out["HEATER_TEMP_C"] = s8(data, idx("x")).toDouble()
        return out
    }

    /**
     * ICCU (header 7E5).
     *
     * AUX_SOC (letter w) was dropped after real driving: it returned 96-99 sometimes and
     * 130-133, 0 and 255 at others, so it is not a percentage and the offset is wrong.
     * AUX_VOLTAGE_V from 220101 stayed in a believable 12.6-14.5 V band throughout and is
     * the better 12V health signal anyway, so nothing useful is lost.
     *
     * Kept as a query because the frame is still worth capturing if we revisit the offsets.
     */
    fun decode22E011(@Suppress("UNUSED_PARAMETER") data: ByteArray): Map<String, Double> = emptyMap()

    /**
     * VMCU (header 7E2). Polled purely to capture raw frames for now: the Ioniq 5 does
     * not answer standard Mode 01 `010D`, so vehicle speed has to come from here, but the
     * byte offset is not known yet and guessing one is how the 220105 display-SOC decode
     * went wrong. Drive at a known steady speed, capture, then run
     * `tools/decode_capture.py --find-speed <kmh>` to derive the offset from real data.
     */
    /**
     * VMCU (header 7E2) — vehicle speed at bytes 11–12, big-endian, ÷100.
     *
     * Derived with the in-app calibration against the dash, because this car does not
     * answer standard Mode 01 `010D`. The unit is whatever the dash was displaying when
     * the calibration value was typed, which is why the result is published as
     * `SPEED_VMCU` rather than the shared `010D` series (defined as km/h) — see
     * [TelemetryFields.SPEED]. Confirm against the dash at two clearly different speeds
     * before trusting it: a single agreeing reading has twice produced a wrong decoder.
     */
    fun decodeVmcuSpeed(data: ByteArray): Map<String, Double> {
        if (data.size <= SPEED_HI + 1) return emptyMap()
        val mph = u16(data, SPEED_HI, SPEED_HI + 1) / 100.0
        // The car tops out around 115 mph, so anything past 150 is a bad frame, not a
        // reading. Observed in the wild: ISO-TP padding (0xAAAA) decoding to 436.9 mph.
        // parseData now trims that padding, but a truncated or garbled frame can still
        // land here, and publishing it would poison both the charts and trip efficiency.
        if (mph < 0 || mph > MAX_PLAUSIBLE_MPH) return emptyMap()
        return mapOf("SPEED_VMCU" to round1(mph))
    }

    private const val SPEED_HI = 11
    private const val MAX_PLAUSIBLE_MPH = 150.0

    /**
     * Cluster (header 7C6) — odometer at bytes 8–11, big-endian, in miles.
     *
     * Derived with the in-app calibration against the dash. This gives exact trip distance
     * as an end-minus-start delta: no GPS permission, and none of the drift that comes
     * from integrating a ~2 s speed sample.
     */
    fun decodeClusterOdometer(data: ByteArray): Map<String, Double> {
        if (data.size <= ODO_START + 3) return emptyMap()
        var raw = 0L
        for (i in 0 until 4) raw = (raw shl 8) or (data[ODO_START + i].toLong() and 0xFF)
        // A plainly impossible reading means the offset drifted or the frame was short;
        // publishing it would silently poison trip distance.
        if (raw <= 0 || raw > 2_000_000) return emptyMap()
        return mapOf("ODOMETER" to raw.toDouble())
    }

    private const val ODO_START = 8

    /**
     * TPMS (header 7A0). Pressure in psi and temperature in °C per corner.
     * Offsets from the Esprit1st Torque CSV; not yet seen against a live frame, so a
     * corner reading 0 most likely means that sensor was asleep rather than flat.
     */
    fun decodeTpms(data: ByteArray): Map<String, Double> {
        if (data.size <= idx("u")) return emptyMap()
        val corners = listOf(
            "FL" to 4, // e
            "FR" to 9, // j
            "RL" to 14, // o
            "RR" to 19, // t
        )
        val out = mutableMapOf<String, Double>()
        for ((corner, base) in corners) {
            val psi = u8(data, base) / 5.0
            // A sleeping sensor reports 0; publishing it would look like a flat tire.
            if (psi > 0) out["TIRE_${corner}_PSI"] = round1(psi)
            out["TIRE_${corner}_C"] = (u8(data, base + 1) - 50).toDouble()
        }
        return out
    }

    /**
     * Climate/body (header 7B3): outside and cabin temperature, plus the cluster's own
     * vehicle speed in km/h — useful as an independent cross-check on the VMCU speed
     * decode, which was derived by calibration rather than from a published offset.
     */
    fun decodeClimate(data: ByteArray): Map<String, Double> {
        val out = mutableMapOf<String, Double>()
        if (data.size > 6) {
            out["INDOOR_TEMP_C"] = round1(u8(data, 5) / 2.0 - 40)
            out["OUTDOOR_TEMP_C"] = round1(u8(data, 6) / 2.0 - 40)
        }
        if (data.size > idx("ad")) out["SPEED_CLUSTER_KMH"] = u8(data, idx("ad")).toDouble()
        return out
    }

    val QUERIES: List<UdsQuery> = listOf(
        // Fast-moving: every cycle, so the history charts have real resolution.
        UdsQuery("7E4", "220101", ::decode220101),
        // Slow-moving: SOH and display SOC barely move within a drive.
        UdsQuery("7E4", "220105", ::decode220105, everyNCycles = 10),
        UdsQuery("7E5", "22E011", ::decode22E011, everyNCycles = 10),
        // Speed moves fast, so poll it every cycle now that the offset is known.
        UdsQuery("7E2", "22E004", ::decodeVmcuSpeed),
        // Odometer moves slowly; polling it often would just cost round trips.
        UdsQuery("7C6", "22B002", ::decodeClusterOdometer, everyNCycles = 10),
        // Tires and cabin temperature change on the scale of minutes, not seconds.
        UdsQuery("7A0", "22C00B", ::decodeTpms, everyNCycles = 30),
        UdsQuery("7B3", "220100", ::decodeClimate, everyNCycles = 15),
    )
}
