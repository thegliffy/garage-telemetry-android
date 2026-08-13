package com.garagepi.telemetry.obd

/** Rolling window for the "now" figure. */
private const val WINDOW_MS = 10_000L

/** Below this the window is too short to divide by; just connected, or a stalled adapter. */
private const val MIN_WINDOW_MS = 3_000L

/** Energy floor in kWh. Under this the denominator is noise and the result meaningless. */
private const val MIN_ENERGY_KWH = 0.0005

/** Gaps longer than this are dropped: the app was probably killed or the adapter stalled. */
private const val MAX_SAMPLE_GAP_MS = 5_000L

private data class Sample(val ts: Long, val miles: Double, val kwh: Double)

/**
 * Live driving efficiency in miles per kWh, over the whole session and over a rolling
 * 10 second window.
 *
 * Distance is integrated from speed rather than read from the odometer: the odometer
 * resolves to 1 mile, so over ten seconds it reads 0 almost always and then jumps.
 * Energy is integrated from pack power the same way [com.garagepi.telemetry.ui.history
 * .TripSummary] does it for completed drives.
 *
 * Not thread safe; the poll loop is the only caller.
 */
class EfficiencyTracker {

    private var lastTs: Long? = null
    private var sessionMiles = 0.0
    private var sessionKwh = 0.0

    /** Per-sample increments, kept only as long as the window needs them. */
    private val window = ArrayDeque<Sample>()

    /**
     * Feed one poll. [speedMph] and [powerKw] are the values just read; power is positive
     * for discharge.
     */
    fun update(timestampMs: Long, speedMph: Double, powerKw: Double) {
        val previous = lastTs
        lastTs = timestampMs
        if (previous == null) return

        val gapMs = timestampMs - previous
        // Out-of-order or absurdly stale samples would otherwise inject a huge bogus
        // increment into both the session and the window.
        if (gapMs <= 0 || gapMs > MAX_SAMPLE_GAP_MS) return

        val hours = gapMs / 3_600_000.0
        val miles = speedMph * hours
        val kwh = powerKw * hours

        sessionMiles += miles
        sessionKwh += kwh

        window.addLast(Sample(timestampMs, miles, kwh))
        while (window.isNotEmpty() && timestampMs - window.first().ts > WINDOW_MS) {
            window.removeFirst()
        }
    }

    /** Efficiency since the tracker was created, or null while it is not yet meaningful. */
    fun sessionEfficiency(): Double? = efficiency(sessionMiles, sessionKwh)

    /** Efficiency over the last ~10 s, or null if the window is too short or degenerate. */
    fun currentEfficiency(): Double? {
        if (window.size < 2) return null
        val span = window.last().ts - window.first().ts
        if (span < MIN_WINDOW_MS) return null
        return efficiency(window.sumOf { it.miles }, window.sumOf { it.kwh })
    }

    fun reset() {
        lastTs = null
        sessionMiles = 0.0
        sessionKwh = 0.0
        window.clear()
    }

    private fun efficiency(miles: Double, kwh: Double): Double? = when {
        // Net regen over the period: efficiency is undefined, not infinite. Reporting a
        // huge number here would look like a spectacular result rather than coasting.
        kwh <= MIN_ENERGY_KWH -> null
        // Stationary with the car awake burns energy while covering no ground. That is a
        // real 0, not a divide-by-almost-nothing spike.
        miles <= 0.0 -> 0.0
        else -> miles / kwh
    }
}
