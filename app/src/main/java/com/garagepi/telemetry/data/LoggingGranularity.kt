package com.garagepi.telemetry.data

/**
 * How densely a drive is written to Room (and therefore synced / charted).
 *
 * Live gauges still update every poll cycle (~0.5 s); this only gates insert. Efficiency
 * is still computed from every poll so a coarser log does not invent a sparse 10 s window.
 */
enum class LoggingGranularity(
    val label: String,
    val description: String,
    /** Minimum gap between persisted samples. Zero means every successful poll. */
    val persistIntervalMs: Long,
) {
    EVERY_POLL(
        "Every poll (~0.5 s)",
        "Highest resolution. A long drive is tens of thousands of rows and will fill " +
            "the phone and Grafana faster.",
        persistIntervalMs = 0L,
    ),
    ONE_SECOND(
        "1 second",
        "About half the storage of every poll. Fine enough for speed and SOC charts.",
        persistIntervalMs = 1_000L,
    ),
    TWO_SECONDS(
        "2 seconds",
        "A good default for road trips — charts stay useful, uploads stay smaller.",
        persistIntervalMs = 2_000L,
    ),
    FIVE_SECONDS(
        "5 seconds",
        "Coarse. Enough for SOC and energy over a drive, not for hard launches.",
        persistIntervalMs = 5_000L,
    ),
    ;

    fun shouldPersist(nowMs: Long, lastPersistMs: Long): Boolean {
        if (persistIntervalMs <= 0L) return true
        // lastPersistMs == 0: first sample of the session always lands.
        if (lastPersistMs == 0L) return true
        return nowMs - lastPersistMs >= persistIntervalMs
    }

    companion object {
        val DEFAULT = EVERY_POLL

        fun fromName(name: String?): LoggingGranularity =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
