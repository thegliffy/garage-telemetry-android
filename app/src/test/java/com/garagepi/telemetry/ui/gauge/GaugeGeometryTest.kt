package com.garagepi.telemetry.ui.gauge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GaugeGeometryTest {

    @Test
    fun `maps a value to its position in range`() {
        assertEquals(0f, fractionOf(0.0, 0.0, 100.0)!!, 0.0001f)
        assertEquals(0.5f, fractionOf(50.0, 0.0, 100.0)!!, 0.0001f)
        assertEquals(1f, fractionOf(100.0, 0.0, 100.0)!!, 0.0001f)
    }

    @Test
    fun `power arc puts zero at 40 percent, not the middle`() {
        // -180 regen .. +270 power is deliberately asymmetric, so zero must sit
        // proportionally: 180/450 = 0.4. Centring it would misreport every reading.
        assertEquals(0.4f, fractionOf(0.0, -180.0, 270.0)!!, 0.0001f)
    }

    @Test
    fun `out of range values pin at the ends`() {
        assertEquals(1f, fractionOf(500.0, -180.0, 270.0)!!, 0.0001f)
        assertEquals(0f, fractionOf(-500.0, -180.0, 270.0)!!, 0.0001f)
    }

    @Test
    fun `degenerate ranges return nothing rather than dividing by zero`() {
        assertNull(fractionOf(5.0, 10.0, 10.0))
        assertNull(fractionOf(5.0, 10.0, 0.0))
    }

    @Test
    fun `sweep spans a half circle`() {
        assertEquals(0f, sweepDegrees(0.0, 0.0, 100.0)!!, 0.0001f)
        assertEquals(90f, sweepDegrees(50.0, 0.0, 100.0)!!, 0.0001f)
        assertEquals(180f, sweepDegrees(100.0, 0.0, 100.0)!!, 0.0001f)
    }

    @Test
    fun `style names round trip for persistence`() {
        TileStyle.entries.forEach { assertEquals(it, TileStyle.fromName(it.name)) }
        assertNull(TileStyle.fromName("NOT_A_STYLE"))
        assertNull(TileStyle.fromName(null))
    }
}
