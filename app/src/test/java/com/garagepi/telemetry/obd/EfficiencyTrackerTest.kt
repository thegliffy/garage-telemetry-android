package com.garagepi.telemetry.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EfficiencyTrackerTest {

    /** Feed a steady state for [seconds], one sample every 500 ms, starting at t=0. */
    private fun EfficiencyTracker.drive(seconds: Int, mph: Double, kw: Double, startMs: Long = 0): Long {
        var t = startMs
        update(t, mph, kw)
        repeat(seconds * 2) {
            t += 500
            update(t, mph, kw)
        }
        return t
    }

    @Test
    fun `steady 60 mph at 20 kW is 3 miles per kWh`() {
        val tracker = EfficiencyTracker()
        tracker.drive(seconds = 60, mph = 60.0, kw = 20.0)
        assertEquals(3.0, tracker.sessionEfficiency()!!, 0.001)
        assertEquals(3.0, tracker.currentEfficiency()!!, 0.001)
    }

    @Test
    fun `stationary while drawing power reads zero, not a spike`() {
        val tracker = EfficiencyTracker()
        tracker.drive(seconds = 30, mph = 0.0, kw = 1.0)
        assertEquals(0.0, tracker.sessionEfficiency()!!, 0.0001)
        assertEquals(0.0, tracker.currentEfficiency()!!, 0.0001)
    }

    @Test
    fun `net regen reports no value rather than infinity`() {
        val tracker = EfficiencyTracker()
        tracker.drive(seconds = 30, mph = 40.0, kw = -25.0)
        assertNull("coasting downhill is not infinitely efficient", tracker.sessionEfficiency())
        assertNull(tracker.currentEfficiency())
    }

    @Test
    fun `no value until the window is long enough`() {
        val tracker = EfficiencyTracker()
        tracker.update(0, 60.0, 20.0)
        assertNull("a single sample cannot define a rate", tracker.currentEfficiency())
        tracker.update(500, 60.0, 20.0)
        assertNull("half a second is too short a window", tracker.currentEfficiency())
    }

    @Test
    fun `current window follows a change while the session average lags`() {
        val tracker = EfficiencyTracker()
        // Efficient for a long time, then abruptly wasteful.
        val t = tracker.drive(seconds = 300, mph = 60.0, kw = 12.0)
        tracker.drive(seconds = 20, mph = 60.0, kw = 60.0, startMs = t + 500)

        val now = tracker.currentEfficiency()!!
        val session = tracker.sessionEfficiency()!!
        assertEquals("window should reflect the recent 60 kW", 1.0, now, 0.05)
        assertTrue("session should still be dominated by the efficient stretch", session > 3.0)
    }

    @Test
    fun `a long gap is dropped instead of inventing distance`() {
        val tracker = EfficiencyTracker()
        tracker.drive(seconds = 30, mph = 60.0, kw = 20.0)
        val before = tracker.sessionEfficiency()!!

        // Process killed for an hour, then a sample arrives. Integrating across that gap
        // would add ~60 miles and ~20 kWh that were never measured.
        tracker.update(3_600_000, 60.0, 20.0)
        assertEquals(before, tracker.sessionEfficiency()!!, 0.001)
    }

    @Test
    fun `out of order samples are ignored`() {
        val tracker = EfficiencyTracker()
        tracker.drive(seconds = 30, mph = 60.0, kw = 20.0)
        val before = tracker.sessionEfficiency()!!
        tracker.update(0, 999.0, 999.0)
        assertEquals(before, tracker.sessionEfficiency()!!, 0.001)
    }

    @Test
    fun `reset clears everything`() {
        val tracker = EfficiencyTracker()
        tracker.drive(seconds = 30, mph = 60.0, kw = 20.0)
        assertNotNull(tracker.sessionEfficiency())
        tracker.reset()
        assertNull(tracker.sessionEfficiency())
        assertNull(tracker.currentEfficiency())
    }
}
