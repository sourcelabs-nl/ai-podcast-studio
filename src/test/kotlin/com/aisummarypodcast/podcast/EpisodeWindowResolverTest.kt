package com.aisummarypodcast.podcast

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.EpisodeProperties
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodeRepository
import com.aisummarypodcast.store.EpisodeStatus
import com.aisummarypodcast.store.Podcast
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class EpisodeWindowResolverTest {

    private val episodeRepository = mockk<EpisodeRepository>()
    private val appProperties = mockk<AppProperties> {
        every { episode } returns EpisodeProperties(maxWindowDays = 7)
    }

    // 15:00 in Europe/Amsterdam is 13:00Z while summer time applies.
    private val mondaySlot = Instant.parse("2026-09-07T13:00:00Z")
    private val tuesdaySlot = Instant.parse("2026-09-08T13:00:00Z")
    private val fridaySlot = Instant.parse("2026-09-11T13:00:00Z")
    private val nextMondaySlot = Instant.parse("2026-09-14T13:00:00Z")

    private val podcast = Podcast(
        id = "pod-1",
        userId = "user-1",
        name = "Daily",
        topic = "AI",
        cron = "0 0 15 * * MON-FRI",
        timezone = "Europe/Amsterdam"
    )

    private fun resolver(now: Instant = tuesdaySlot) = EpisodeWindowResolver(
        episodeRepository, appProperties, Clock.fixed(now, ZoneOffset.UTC)
    )

    private fun noCoverage() {
        every { episodeRepository.findLatestCoveredWindowEnd(any(), any()) } returns null
    }

    private fun coveredUntil(windowEnd: String) {
        every { episodeRepository.findLatestCoveredWindowEnd(any(), any()) } returns windowEnd
    }

    @Test
    fun `a weekday window covers the 24 hours since the previous slot`() {
        noCoverage()

        val window = resolver().resolve(podcast, tuesdaySlot)

        assertEquals(mondaySlot, window.start)
        assertEquals(tuesdaySlot, window.end)
    }

    @Test
    fun `a Monday window reaches back across the weekend to Friday`() {
        noCoverage()

        val window = resolver().resolve(podcast, nextMondaySlot)

        assertEquals(fridaySlot, window.start)
        assertEquals(nextMondaySlot, window.end)
    }

    @Test
    fun `the window extends back over a slot no episode covered`() {
        // Monday's episode failed, so nothing covers Monday 15:00 to Tuesday 15:00. Tuesday's window
        // has to reach back to where coverage actually stopped or that day's content is orphaned.
        coveredUntil(mondaySlot.toString())

        val window = resolver().resolve(podcast, nextMondaySlot)

        assertEquals(mondaySlot, window.start)
    }

    @Test
    fun `an extended window is capped at maxWindowDays`() {
        coveredUntil("2026-08-01T13:00:00Z")

        val window = resolver().resolve(podcast, tuesdaySlot)

        assertEquals(tuesdaySlot.minusSeconds(7 * 24 * 3600), window.start)
    }

    @Test
    fun `coverage past the previous slot does not shorten the window`() {
        coveredUntil(tuesdaySlot.toString())

        val window = resolver().resolve(podcast, tuesdaySlot)

        assertEquals(mondaySlot, window.start)
    }

    @Test
    fun `an unparseable cron falls back to the cap`() {
        noCoverage()
        val broken = podcast.copy(cron = "not a cron")

        val window = resolver().resolve(broken, tuesdaySlot)

        assertEquals(tuesdaySlot.minusSeconds(7 * 24 * 3600), window.start)
    }

    @Test
    fun `an invalid timezone falls back to UTC`() {
        noCoverage()
        val broken = podcast.copy(timezone = "Mars/Olympus_Mons")

        // 15:00 UTC on the Monday, because the cron is now read in UTC.
        val window = resolver().resolve(broken, Instant.parse("2026-09-08T15:00:00Z"))

        assertEquals(Instant.parse("2026-09-07T15:00:00Z"), window.start)
    }

    @Test
    fun `resolveForNow ends the window at the current instant`() {
        noCoverage()
        val now = Instant.parse("2026-09-08T09:30:00Z")

        val window = resolver(now).resolveForNow(podcast)

        assertEquals(now, window.end)
        assertEquals(mondaySlot, window.start)
    }

    @Test
    fun `windowOf reads the window stored on an episode`() {
        val episode = episode(windowStart = mondaySlot.toString(), windowEnd = tuesdaySlot.toString())

        val window = resolver().windowOf(episode)

        assertEquals(EpisodeWindow(mondaySlot, tuesdaySlot), window)
    }

    @Test
    fun `windowOf returns null for an episode without a window`() {
        assertNull(resolver().windowOf(episode()))
    }

    @Test
    fun `windowOf returns null for an unparseable window`() {
        val episode = episode(windowStart = "yesterday", windowEnd = "today")

        assertNull(resolver().windowOf(episode))
    }

    private fun episode(windowStart: String? = null, windowEnd: String? = null) = Episode(
        id = 1,
        podcastId = "pod-1",
        generatedAt = tuesdaySlot.toString(),
        windowStart = windowStart,
        windowEnd = windowEnd,
        scriptText = "script",
        status = EpisodeStatus.GENERATED
    )
}
