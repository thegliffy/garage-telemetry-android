package com.garagepi.telemetry.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the field metadata the UI depends on. These are cheap to get wrong by hand across
 * forty-odd definitions, and wrong precision or a missing sibling is invisible until the
 * car is in front of you.
 */
class TelemetryFieldTest {

    @Test
    fun `speed shows whole numbers`() {
        assertEquals(0, TelemetryFields.SPEED.decimals)
    }

    @Test
    fun `cell voltage keeps two decimals`() {
        assertEquals(2, TelemetryFields.CELL_V_MAX.decimals)
        assertEquals(2, TelemetryFields.CELL_V_MIN.decimals)
    }

    @Test
    fun `efficiency keeps two decimals`() {
        assertEquals(2, TelemetryFields.EFF_NOW.decimals)
        assertEquals(2, TelemetryFields.EFF_SESSION.decimals)
    }

    @Test
    fun `charge flags are booleans`() {
        listOf(
            TelemetryFields.HV_CHARGING,
            TelemetryFields.AC_PLUG,
            TelemetryFields.CCS_PLUG,
            TelemetryFields.BATT_HEATER,
        )
            .forEach { assertTrue("${it.pid} should be boolean", it.isBoolean) }
    }

    @Test
    fun `measured values are not booleans`() {
        listOf(TelemetryFields.SPEED, TelemetryFields.HV_SOC, TelemetryFields.PACK_POWER)
            .forEach { assertFalse("${it.pid} should not be boolean", it.isBoolean) }
    }

    @Test
    fun `composite tile anchors are selectable and their siblings are not`() {
        // The anchors render every sibling, so listing the siblings too would be eleven
        // near-identical picker entries.
        listOf(
            TelemetryFields.TIRE_FL,
            TelemetryFields.TIRE_FL_TEMP,
            TelemetryFields.MOTOR_RPM_FRONT,
            TelemetryFields.OUTDOOR_TEMP,
        ).forEach {
            assertNotNull("${it.pid} anchors a tile and must be selectable",
                TelemetryFields.bySelectablePid(it.pid))
        }
        listOf(
            TelemetryFields.TIRE_FR, TelemetryFields.TIRE_RL, TelemetryFields.TIRE_RR,
            TelemetryFields.TIRE_FR_TEMP, TelemetryFields.TIRE_RL_TEMP, TelemetryFields.TIRE_RR_TEMP,
            TelemetryFields.MOTOR_RPM_REAR,
            TelemetryFields.INDOOR_TEMP,
        ).forEach {
            assertEquals("${it.pid} is shown by its anchor and should not be listed separately",
                null, TelemetryFields.bySelectablePid(it.pid))
        }
    }

    @Test
    fun `history drops pack power and battery health charts`() {
        val single = TelemetryFields.HISTORY_SINGLE_CHARTS.map { it.pid }.toSet()
        val allCharts = TelemetryFields.CHART_FIELDS.map { it.pid }.toSet()
        assertTrue(TelemetryFields.PACK_CURRENT.pid in single)
        assertTrue(TelemetryFields.PACK_POWER.pid !in single)
        assertTrue(TelemetryFields.PACK_POWER.pid !in allCharts)
        assertTrue(TelemetryFields.HV_SOH.pid !in allCharts)
        assertTrue(TelemetryFields.BATT_TEMP.pid in allCharts)
        assertTrue(TelemetryFields.BATT_TEMP_MIN.pid in allCharts)
        assertTrue(TelemetryFields.MOTOR_RPM_FRONT.pid in allCharts)
        assertTrue(TelemetryFields.MOTOR_RPM_REAR.pid in allCharts)
    }

    @Test
    fun `dropped decoders are not offered as tiles`() {
        // AUX_SOC read 96-99 and 130-133 and 0 and 255 on the same car; it is not a
        // percentage and is no longer decoded, so a tile for it would never fill.
        assertEquals(null, TelemetryFields.bySelectablePid(TelemetryFields.AUX_SOC.pid))
    }

    @Test
    fun `no field is listed twice`() {
        // A duplicate shows up twice in the picker and silently shifts the default layout.
        val duplicates = TelemetryFields.SELECTABLE.groupBy { it.pid }.filter { it.value.size > 1 }
        assertTrue("duplicated in SELECTABLE: ${duplicates.keys}", duplicates.isEmpty())
    }

    @Test
    fun `default tile layout fills every slot`() {
        assertTrue(
            "SELECTABLE must cover the default grid",
            TelemetryFields.SELECTABLE.size >= 8,
        )
    }

    @Test
    fun `pack power range is asymmetric with regen the smaller side`() {
        val power = TelemetryFields.PACK_POWER
        assertEquals(-180.0, power.min, 0.001)
        assertEquals(270.0, power.max, 0.001)
        assertTrue("regen headroom should be less than drive power", -power.min < power.max)
    }
}
