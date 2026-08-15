package com.garagepi.telemetry.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoggingGranularityTest {
    @Test
    fun `every poll always persists`() {
        assertTrue(LoggingGranularity.EVERY_POLL.shouldPersist(nowMs = 1_000, lastPersistMs = 999))
    }

    @Test
    fun `first sample of a session always persists`() {
        assertTrue(LoggingGranularity.TWO_SECONDS.shouldPersist(nowMs = 500, lastPersistMs = 0))
    }

    @Test
    fun `two-second gate skips until the interval elapses`() {
        val g = LoggingGranularity.TWO_SECONDS
        assertFalse(g.shouldPersist(nowMs = 1_500, lastPersistMs = 1_000))
        assertTrue(g.shouldPersist(nowMs = 3_000, lastPersistMs = 1_000))
    }

    @Test
    fun `unknown stored name falls back to default`() {
        assertEquals(LoggingGranularity.DEFAULT, LoggingGranularity.fromName(null))
        assertEquals(LoggingGranularity.DEFAULT, LoggingGranularity.fromName("not_a_real_value"))
    }
}
