package com.garagepi.telemetry.ui.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartSeriesTest {
    @Test
    fun `downsample keeps short series unchanged`() {
        val points = (1..10).toList()
        assertEquals(points, ChartSeries.downsample(points, maxPoints = 400))
    }

    @Test
    fun `downsample bounds length and keeps endpoints`() {
        val points = (0 until 10_000).toList()
        val out = ChartSeries.downsample(points, maxPoints = 400)
        assertEquals(400, out.size)
        assertEquals(0, out.first())
        assertEquals(9_999, out.last())
        assertTrue(out.zipWithNext().all { (a, b) -> a <= b })
    }
}
