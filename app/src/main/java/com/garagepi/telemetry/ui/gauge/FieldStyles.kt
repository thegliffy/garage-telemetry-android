package com.garagepi.telemetry.ui.gauge

import com.garagepi.telemetry.obd.TelemetryField
import com.garagepi.telemetry.obd.TelemetryFields

/**
 * Which gauge styles suit which field.
 *
 * Kept in the UI layer rather than on [TelemetryField] so the OBD model does not depend on
 * presentation. Derived from the field's own properties, so a newly added field gets
 * sensible options without being listed here.
 */
object FieldStyles {

    /** Anchors for the composite tiles that render several readings at once. */
    private val TIRE_ANCHORS = setOf(TelemetryFields.TIRE_FL.pid, TelemetryFields.TIRE_FL_TEMP.pid)
    private val MOTOR_ANCHOR = TelemetryFields.MOTOR_RPM_FRONT.pid
    private val CLIMATE_ANCHOR = TelemetryFields.OUTDOOR_TEMP.pid

    fun supported(field: TelemetryField): List<TileStyle> = buildList {
        add(TileStyle.NUMBER)
        if (field.hasRange) {
            add(TileStyle.ARC)
            // Only meaningful when the value actually goes negative; a 0-100 arc with a
            // "zero point" would just be the arc again.
            if (field.min < 0) add(TileStyle.POWER_ARC)
            if (field.isTemperature) add(TileStyle.THERMOMETER)
        }
        if (field.pid == TelemetryFields.BATT_TEMP.pid) add(TileStyle.BATT_TEMP_PAIR)
        if (field.pid in TIRE_ANCHORS) add(TileStyle.TIRE_QUAD)
        if (field.pid == MOTOR_ANCHOR) add(TileStyle.MOTOR_PAIR)
        if (field.pid == CLIMATE_ANCHOR) add(TileStyle.CLIMATE_PAIR)
    }

    fun default(field: TelemetryField): TileStyle = when {
        field.pid == TelemetryFields.BATT_TEMP.pid -> TileStyle.BATT_TEMP_PAIR
        field.pid in TIRE_ANCHORS -> TileStyle.TIRE_QUAD
        field.pid == MOTOR_ANCHOR -> TileStyle.MOTOR_PAIR
        field.pid == CLIMATE_ANCHOR -> TileStyle.CLIMATE_PAIR
        !field.hasRange -> TileStyle.NUMBER
        field.signedFlow || field.min < 0 && !field.isTemperature -> TileStyle.POWER_ARC
        field.isTemperature -> TileStyle.THERMOMETER
        else -> TileStyle.ARC
    }

    /** Falls back to the default when a stored style is no longer valid for the field. */
    fun resolve(field: TelemetryField, stored: TileStyle?): TileStyle =
        stored?.takeIf { it in supported(field) } ?: default(field)
}
