package com.garagepi.telemetry.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitsTest {

    @Test
    fun `converts celsius to fahrenheit`() {
        assertEquals(32.0, Units.celsiusToFahrenheit(0.0), 0.001)
        assertEquals(212.0, Units.celsiusToFahrenheit(100.0), 0.001)
        assertEquals(-40.0, Units.celsiusToFahrenheit(-40.0), 0.001)
        // A real battery reading from the car.
        assertEquals(86.0, Units.celsiusToFahrenheit(30.0), 0.001)
    }

    @Test
    fun `converts the gauge range along with the value`() {
        // Converting the value but not the range would leave 86F pinned near the top of a
        // -40..80 scale, which is the whole reason this is a field-level transform.
        val f = Units.forDisplay(TelemetryFields.BATT_TEMP, fahrenheit = true)
        assertEquals("°F", f.unit)
        assertEquals(-40.0, f.min, 0.001)
        assertEquals(176.0, f.max, 0.001)
    }

    @Test
    fun `celsius setting leaves the field untouched`() {
        val original = TelemetryFields.BATT_TEMP
        assertSame(original, Units.forDisplay(original, fahrenheit = false))
    }

    @Test
    fun `non-temperature fields are never converted`() {
        listOf(TelemetryFields.SPEED, TelemetryFields.PACK_VOLTAGE, TelemetryFields.HV_SOC)
            .forEach { assertSame(it, Units.forDisplay(it, fahrenheit = true)) }
    }

    @Test
    fun `converts every temperature reading including composite tile siblings`() {
        // The tire and battery tiles read sibling pids straight from this map, so a partial
        // conversion would show one corner in F next to three in C.
        val values = mapOf(
            TelemetryFields.BATT_TEMP.pid to 30.0,
            TelemetryFields.BATT_TEMP_MIN.pid to 29.0,
            TelemetryFields.TIRE_FL_TEMP.pid to 20.0,
            TelemetryFields.TIRE_FR_TEMP.pid to 20.0,
            TelemetryFields.TIRE_RL_TEMP.pid to 20.0,
            TelemetryFields.TIRE_RR_TEMP.pid to 20.0,
            TelemetryFields.OUTDOOR_TEMP.pid to 15.0,
            TelemetryFields.HEATER_TEMP.pid to 32.0,
        )
        val out = Units.forDisplay(values, fahrenheit = true)
        assertEquals(86.0, out[TelemetryFields.BATT_TEMP.pid]!!, 0.001)
        assertEquals(84.2, out[TelemetryFields.BATT_TEMP_MIN.pid]!!, 0.001)
        assertEquals(59.0, out[TelemetryFields.OUTDOOR_TEMP.pid]!!, 0.001)
        listOf(
            TelemetryFields.TIRE_FL_TEMP, TelemetryFields.TIRE_FR_TEMP,
            TelemetryFields.TIRE_RL_TEMP, TelemetryFields.TIRE_RR_TEMP,
        ).forEach { assertEquals("${it.pid} must convert", 68.0, out[it.pid]!!, 0.001) }
    }

    @Test
    fun `leaves non-temperature readings alone in a mixed map`() {
        val values = mapOf(
            TelemetryFields.BATT_TEMP.pid to 30.0,
            TelemetryFields.SPEED.pid to 65.0,
            TelemetryFields.PACK_VOLTAGE.pid to 714.7,
            TelemetryFields.HV_SOC.pid to 55.0,
        )
        val out = Units.forDisplay(values, fahrenheit = true)
        assertEquals(65.0, out[TelemetryFields.SPEED.pid]!!, 0.001)
        assertEquals(714.7, out[TelemetryFields.PACK_VOLTAGE.pid]!!, 0.001)
        assertEquals(55.0, out[TelemetryFields.HV_SOC.pid]!!, 0.001)
    }

    @Test
    fun `every temperature pid the decoders emit is recognised`() {
        // A pid missing from the lookup would silently stay in Celsius while its
        // neighbours converted.
        listOf(
            "BATT_TEMP_MAX_C", "BATT_TEMP_MIN_C", "HEATER_TEMP_C",
            "OUTDOOR_TEMP_C", "INDOOR_TEMP_C",
            "TIRE_FL_C", "TIRE_FR_C", "TIRE_RL_C", "TIRE_RR_C",
        ).forEach {
            assertTrue("$it should be known as a temperature", TelemetryFields.isTemperaturePid(it))
        }
    }

    @Test
    fun `decoders still emit celsius regardless of the display setting`() {
        // The setting must never reach decoding: garagepi writes the same readings series
        // from the Pi, and a unit that depends on a phone preference would corrupt it.
        val frame = ByteArray(60).apply {
            this[0] = 0xEF.toByte(); this[4] = 0x6E // SOC
            this[14] = 30 // O -> BATT_TEMP_MAX_C
            this[15] = 29 // P -> BATT_TEMP_MIN_C
        }
        val decoded = IoniqUds.decode220101(frame)
        assertEquals(30.0, decoded["BATT_TEMP_MAX_C"]!!, 0.001)
        assertEquals(29.0, decoded["BATT_TEMP_MIN_C"]!!, 0.001)
    }
}
