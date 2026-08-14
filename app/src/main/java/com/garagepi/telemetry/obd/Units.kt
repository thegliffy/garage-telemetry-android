package com.garagepi.telemetry.obd

/**
 * Display-only unit conversion.
 *
 * Readings are decoded, stored and uploaded in the units the car reports — Celsius for
 * temperatures. garagepi writes the same `readings` series from the Pi, so converting
 * before storage would make the two disagree about what a number means. Conversion
 * happens at the UI boundary and nowhere else.
 */
object Units {

    fun celsiusToFahrenheit(c: Double): Double = c * 9.0 / 5.0 + 32.0

    /**
     * The field as it should be *displayed*, with its gauge range converted too.
     *
     * Converting the value without the range would leave a thermometer showing 86 °F
     * against a −40..80 scale — pinned near the top and completely wrong. The conversion
     * is linear, so applying it to the endpoints keeps the needle where it belongs.
     */
    fun forDisplay(field: TelemetryField, fahrenheit: Boolean): TelemetryField =
        if (!fahrenheit || !field.isTemperature) {
            field
        } else {
            field.copy(
                unit = "°F",
                min = celsiusToFahrenheit(field.min),
                max = celsiusToFahrenheit(field.max),
            )
        }

    /**
     * Converts every temperature reading in a latest-values map, leaving the rest alone.
     *
     * Done wholesale rather than per-tile because the composite tiles — all four tire
     * temperatures, battery hi/low — read sibling pids straight out of this map, and
     * converting only the tile's own field would show one value in Fahrenheit next to
     * three in Celsius.
     */
    fun forDisplay(values: Map<String, Double>, fahrenheit: Boolean): Map<String, Double> {
        if (!fahrenheit) return values
        return values.mapValues { (pid, value) ->
            if (TelemetryFields.isTemperaturePid(pid)) celsiusToFahrenheit(value) else value
        }
    }
}
