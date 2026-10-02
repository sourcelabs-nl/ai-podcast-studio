package com.aisummarypodcast.store

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class SourceTest {

    private val now = Instant.parse("2026-01-01T12:00:00Z")

    private fun source(
        pollIntervalMinutes: Int = 60,
        consecutiveFailures: Int = 0,
        lastPolledMinutesAgo: Long? = null,
        maxBackoffHours: Int? = null
    ) = Source(
        id = "s1", podcastId = "p1", type = SourceType.RSS, url = "u",
        pollIntervalMinutes = pollIntervalMinutes,
        consecutiveFailures = consecutiveFailures,
        lastPolled = lastPolledMinutesAgo?.let { now.minus(it, ChronoUnit.MINUTES).toString() },
        maxBackoffHours = maxBackoffHours
    )

    @Test
    fun `never polled source is due`() {
        assertTrue(source().isDue(24, now))
    }

    @Test
    fun `source is due when its interval has elapsed`() {
        assertTrue(source(lastPolledMinutesAgo = 61).isDue(24, now))
    }

    @Test
    fun `source is not due before its interval has elapsed`() {
        assertFalse(source(lastPolledMinutesAgo = 59).isDue(24, now))
    }

    @Test
    fun `failing source is not due until its backoff interval has elapsed`() {
        assertFalse(source(consecutiveFailures = 2, lastPolledMinutesAgo = 100).isDue(24, now))
        assertTrue(source(consecutiveFailures = 1, lastPolledMinutesAgo = 130).isDue(24, now))
    }

    @Test
    fun `effective interval is the normal interval when no failures`() {
        assertEquals(60L, source().effectivePollIntervalMinutes(24))
    }

    @Test
    fun `effective interval doubles on first failure`() {
        assertEquals(120L, source(consecutiveFailures = 1).effectivePollIntervalMinutes(24))
    }

    @Test
    fun `effective interval is 8x on third failure`() {
        assertEquals(480L, source(consecutiveFailures = 3).effectivePollIntervalMinutes(24))
    }

    @Test
    fun `effective interval is capped at the default max backoff hours`() {
        assertEquals(1440L, source(consecutiveFailures = 10).effectivePollIntervalMinutes(24))
        assertEquals(360L, source(consecutiveFailures = 10).effectivePollIntervalMinutes(6))
    }

    @Test
    fun `per-source max backoff hours overrides the default`() {
        assertEquals(2880L, source(consecutiveFailures = 10, maxBackoffHours = 48).effectivePollIntervalMinutes(24))
    }
}
