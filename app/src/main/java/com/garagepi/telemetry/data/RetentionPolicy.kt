package com.garagepi.telemetry.data

import java.util.concurrent.TimeUnit

/**
 * How long finished sessions are kept on the phone. Applied by RetentionWorker.
 *
 * The age-based policies are a *strict* limit: a session older than the window is
 * deleted whether or not it ever reached the server. That is deliberate (predictable
 * disk usage), and the settings screen says so, but it does mean a drive recorded
 * while away from home can be lost if it ages out before you get back on the network.
 */
enum class RetentionPolicy(val label: String, val description: String) {
    ONE_MONTH(
        "1 month",
        "Delete sessions more than 30 days old, even if they were never uploaded.",
    ),
    ONE_YEAR(
        "1 year",
        "Delete sessions more than 365 days old, even if they were never uploaded.",
    ),
    FOREVER(
        "Keep indefinitely",
        "Never delete anything. Storage grows by roughly 1 MB per hour of driving.",
    ),
    UNTIL_UPLOADED(
        "Until uploaded",
        "Delete a session as soon as it is fully uploaded and closed on the server. " +
            "Nothing is deleted while sync is unconfigured or unreachable.",
    ),
    ;

    /** Cutoff timestamp for age-based policies; null for the policies that don't use one. */
    fun cutoffMillis(now: Long = System.currentTimeMillis()): Long? = when (this) {
        ONE_MONTH -> now - TimeUnit.DAYS.toMillis(30)
        ONE_YEAR -> now - TimeUnit.DAYS.toMillis(365)
        FOREVER, UNTIL_UPLOADED -> null
    }

    companion object {
        val DEFAULT = FOREVER

        /** Tolerant of unknown/legacy stored values — falls back to the safe default. */
        fun fromName(name: String?): RetentionPolicy =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
