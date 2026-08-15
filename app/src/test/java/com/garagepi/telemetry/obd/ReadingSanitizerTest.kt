package com.garagepi.telemetry.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingSanitizerTest {

    private fun speed(value: Double, ts: Long = 1_000L) =
        PidReading(TelemetryFields.SPEED.pid, value, ts)

    @Test
    fun `tosses speed over 150 mph`() {
        val sanitizer = ReadingSanitizer()
        assertTrue(sanitizer.filter(listOf(speed(151.0))).isEmpty())
        assertTrue(sanitizer.filter(listOf(speed(436.9))).isEmpty())
    }

    @Test
    fun `keeps a real highway speed`() {
        val sanitizer = ReadingSanitizer()
        val kept = sanitizer.filter(listOf(speed(72.0)))
        assertEquals(1, kept.size)
        assertEquals(72.0, kept[0].value, 0.001)
    }

    @Test
    fun `keeps the 150 mph ceiling`() {
        val sanitizer = ReadingSanitizer()
        assertEquals(1, sanitizer.filter(listOf(speed(150.0))).size)
    }

    @Test
    fun `tosses a single-frame speed jump that the car cannot make`() {
        val sanitizer = ReadingSanitizer()
        sanitizer.filter(listOf(speed(65.0, ts = 1_000)))
        // 120 is under the 150 cap, so only the jump rule can reject it.
        val kept = sanitizer.filter(listOf(speed(120.0, ts = 1_800)))
        assertTrue("65 → 120 mph in 0.8s is a bad frame", kept.isEmpty())
    }

    @Test
    fun `keeps a hard acceleration that is still physically possible`() {
        val sanitizer = ReadingSanitizer()
        sanitizer.filter(listOf(speed(20.0, ts = 1_000)))
        val kept = sanitizer.filter(listOf(speed(45.0, ts = 2_000)))
        assertEquals(1, kept.size)
    }

    @Test
    fun `after a long gap a new speed may re-anchor`() {
        val sanitizer = ReadingSanitizer()
        sanitizer.filter(listOf(speed(10.0, ts = 1_000)))
        val kept = sanitizer.filter(listOf(speed(70.0, ts = 1_000 + 6_000)))
        assertEquals(1, kept.size)
    }

    @Test
    fun `tosses a 255 cluster-speed byte`() {
        val sanitizer = ReadingSanitizer()
        val reading = PidReading(TelemetryFields.SPEED_CLUSTER.pid, 255.0, 1L)
        assertTrue(sanitizer.filter(listOf(reading)).isEmpty())
    }

    @Test
    fun `tosses NaN and infinite`() {
        val sanitizer = ReadingSanitizer()
        assertFalse(sanitizer.accept(speed(Double.NaN)))
        assertFalse(sanitizer.accept(speed(Double.POSITIVE_INFINITY)))
    }

    @Test
    fun `tosses SOC outside 0-100 but keeps the rest of the poll`() {
        val sanitizer = ReadingSanitizer()
        val kept = sanitizer.filter(
            listOf(
                PidReading("HV_SOC", 254.0, 1L),
                PidReading("PACK_VOLTAGE_V", 700.0, 1L),
            ),
        )
        assertEquals(listOf("PACK_VOLTAGE_V"), kept.map { it.pid })
    }

    @Test
    fun `reset forgets the last speed so a new session is not compared to the last drive`() {
        val sanitizer = ReadingSanitizer()
        sanitizer.filter(listOf(speed(70.0, ts = 1_000)))
        sanitizer.reset()
        val kept = sanitizer.filter(listOf(speed(5.0, ts = 1_100)))
        assertEquals(1, kept.size)
    }
}
