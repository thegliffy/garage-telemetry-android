package com.garagepi.telemetry.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The calibration screen is only trustworthy if this scan is. It runs in the car where
 * nothing can be debugged, so the arithmetic is pinned down here instead.
 */
class CalibrationScanTest {

    private fun bytes(vararg v: Int) = v.map { it.toByte() }.toByteArray()

    @Test
    fun `extracts big-endian multi-byte value`() {
        val data = bytes(0x00, 0x30, 0x39, 0x00)
        val spec = CandidateSpec("22B002", offset = 1, width = 2, littleEndian = false, divisor = 1.0)
        assertEquals(12345.0, spec.extract(data)!!, 0.001)
    }

    @Test
    fun `extracts little-endian multi-byte value`() {
        val data = bytes(0x39, 0x30)
        val spec = CandidateSpec("22B002", offset = 0, width = 2, littleEndian = true, divisor = 1.0)
        assertEquals(12345.0, spec.extract(data)!!, 0.001)
    }

    @Test
    fun `applies divisor`() {
        val data = bytes(0x03, 0x20)
        val spec = CandidateSpec("22E004", offset = 0, width = 2, littleEndian = false, divisor = 10.0)
        assertEquals(80.0, spec.extract(data)!!, 0.001)
    }

    @Test
    fun `returns null rather than crashing when the field runs past the frame`() {
        val data = bytes(0x01, 0x02)
        assertNull(CandidateSpec("x", offset = 1, width = 4, littleEndian = false, divisor = 1.0).extract(data))
        assertNull(CandidateSpec("x", offset = -1, width = 1, littleEndian = false, divisor = 1.0).extract(data))
    }

    @Test
    fun `scan finds a planted odometer`() {
        val data = bytes(0x00, 0x30, 0x39, 0x00, 0x00, 0x00)
        val found = CalibrationScan.scan("22B002", data, 12345.0, 1.0, CalibrationScan.ODOMETER_WIDTHS)
        assertTrue("expected the planted field to be among the candidates", found.isNotEmpty())
        assertNotNull(found.firstOrNull { it.offset == 1 && it.width == 2 && !it.littleEndian })
        found.forEach { assertEquals(12345.0, it.extract(data)!!, 1.0) }
    }

    @Test
    fun `high byte value is not mistaken for a small speed`() {
        // A frame full of large values must not yield a 0 km/h "match" by accident.
        val data = bytes(0xFF, 0xFE, 0xFD)
        val found = CalibrationScan.scan("22E004", data, 65.0, 2.0, CalibrationScan.SPEED_WIDTHS)
        found.forEach { assertEquals(65.0, it.extract(data)!!, 2.0) }
    }

    @Test
    fun `second sample eliminates coincidental matches`() {
        // Two fields read 100 in the first frame; only offset 0 still reads the odometer
        // after it advances to 150, which is exactly the ambiguity narrow() exists to kill.
        val first = bytes(0x00, 0x64, 0x64, 0x00)
        val candidates = CalibrationScan.scan("22B002", first, 100.0, 1.0, listOf(1, 2))
        assertTrue("both fields should match the first sample", candidates.size >= 2)

        val second = bytes(0x00, 0x96, 0x64, 0x00)
        val narrowed = CalibrationScan.narrow(candidates, second, 150.0, 1.0)

        assertTrue("at least one candidate should survive", narrowed.isNotEmpty())
        narrowed.forEach { assertEquals(150.0, it.extract(second)!!, 1.0) }
        // The byte stuck at 0x64 must be gone.
        assertTrue(narrowed.none { it.offset == 2 && it.width == 1 })
    }

    @Test
    fun `narrow returns empty when nothing explains the new sample`() {
        val first = bytes(0x00, 0x64)
        val candidates = CalibrationScan.scan("22B002", first, 100.0, 1.0, listOf(1, 2))
        val unrelated = bytes(0x00, 0x01)
        assertTrue(CalibrationScan.narrow(candidates, unrelated, 999.0, 1.0).isEmpty())
    }
}
