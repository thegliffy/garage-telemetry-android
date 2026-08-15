package com.garagepi.telemetry.obd

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FastChargeDetectorTest {
    @Test
    fun `ccs plug enters after two polls`() {
        val d = FastChargeDetector()
        d.update(mapOf(TelemetryFields.CCS_PLUG.pid to 1.0))
        assertFalse(d.active)
        d.update(mapOf(TelemetryFields.CCS_PLUG.pid to 1.0))
        assertTrue(d.active)
    }

    @Test
    fun `high charge power without ccs still counts as dc`() {
        val d = FastChargeDetector(enterStreak = 1, exitStreak = 1)
        d.update(mapOf(TelemetryFields.PACK_POWER.pid to -40.0))
        assertTrue(d.active)
    }

    @Test
    fun `ac-rate power without ccs does not enter`() {
        val d = FastChargeDetector(enterStreak = 1, exitStreak = 1)
        d.update(mapOf(TelemetryFields.PACK_POWER.pid to -8.0))
        assertFalse(d.active)
    }

    @Test
    fun `exits only after a streak of no evidence`() {
        val d = FastChargeDetector(enterStreak = 1, exitStreak = 3)
        d.update(mapOf(TelemetryFields.CCS_PLUG.pid to 1.0))
        assertTrue(d.active)
        repeat(2) { d.update(emptyMap()) }
        assertTrue(d.active)
        d.update(emptyMap())
        assertFalse(d.active)
    }

    @Test
    fun `heater on when element is hotter than the pack`() {
        assertTrue(FastChargeDetector.heaterOn(heaterTempC = 42.0, packMaxC = 12.0))
        assertFalse(FastChargeDetector.heaterOn(heaterTempC = 13.0, packMaxC = 12.0))
        assertFalse(FastChargeDetector.heaterOn(heaterTempC = null, packMaxC = 12.0))
    }
}
