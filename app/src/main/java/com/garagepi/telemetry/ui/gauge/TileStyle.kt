package com.garagepi.telemetry.ui.gauge

/** How a dashboard tile draws its value. */
enum class TileStyle(val label: String) {
    NUMBER("Number"),
    ARC("Arc"),
    POWER_ARC("Power arc"),
    THERMOMETER("Thermometer"),

    /** Two live markers: pack hottest and coldest point, and the spread between them. */
    BATT_TEMP_PAIR("Hi/low temp"),

    /** All four corners in a 2x2 laid out as the car sits, front pair on top. */
    TIRE_QUAD("Four corners"),

    /** Front above rear, each with its own bidirectional bar. */
    MOTOR_PAIR("Front/rear"),

    /** Cabin and outside on one standard-height tile. */
    CLIMATE_PAIR("Climate"),
    ;

    companion object {
        fun fromName(name: String?): TileStyle? = entries.firstOrNull { it.name == name }
    }
}

/**
 * Where a value sits in its range, as 0..1. Clamped, so an out-of-range reading pins at an
 * end instead of drawing outside the gauge.
 *
 * Returns null for a zero-width or inverted range rather than dividing by zero — a field
 * with a bad range should render as nothing, not crash or draw garbage.
 */
fun fractionOf(value: Double, min: Double, max: Double): Float? {
    if (max <= min) return null
    return (((value - min) / (max - min)).coerceIn(0.0, 1.0)).toFloat()
}

/**
 * Sweep angle in degrees for a 180° gauge that starts at the left (180°) and sweeps
 * clockwise to the right (360°).
 */
fun sweepDegrees(value: Double, min: Double, max: Double): Float? =
    fractionOf(value, min, max)?.let { it * 180f }
