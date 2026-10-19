package com.aisummarypodcast.scheduler

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.EpisodeProperties
import com.aisummarypodcast.config.SourceProperties
import com.aisummarypodcast.podcast.EpisodeWindow
import com.aisummarypodcast.podcast.EpisodeWindowResolver
import com.aisummarypodcast.source.SourceService
import com.aisummarypodcast.podcast.GenerateBriefingResult
import com.aisummarypodcast.podcast.PodcastService
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodeStatus
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.Source
import com.aisummarypodcast.store.SourceType
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class BriefingGenerationSchedulerTest {

    private val podcastService = mockk<PodcastService>()
    // Relaxed: lastPollRoundCompletedAt defaults to null (stale), pollPodcastSourcesNow is a no-op.
    private val sourcePollingScheduler = mockk<SourcePollingScheduler>(relaxed = true)
    private val appProperties = mockk<AppProperties> {
        every { source } returns SourceProperties()
        every { episode } returns EpisodeProperties()
    }

    // Relaxed: no source is behind the window unless a test says so.
    private val sourceService = mockk<SourceService> {
        every { findSourcesBehindWindow(any(), any()) } returns emptyList()
    }
    private val episodeWindowResolver = mockk<EpisodeWindowResolver> {
        every { zoneOf(any()) } answers { ZoneId.of(firstArg<Podcast>().timezone) }
        every { resolve(any(), any()) } answers {
            val end = secondArg<Instant>()
            EpisodeWindow(start = end.minus(1, ChronoUnit.DAYS), end = end)
        }
    }

    // Fixed clock at 00:05 UTC — 5 minutes after the midnight cron trigger (same day)
    private val defaultNow = Instant.parse("2026-02-23T00:05:00Z")
    private val defaultClock = Clock.fixed(defaultNow, ZoneOffset.UTC)

    private val scheduler = BriefingGenerationScheduler(
        podcastService, sourcePollingScheduler, sourceService, episodeWindowResolver, appProperties, defaultClock
    )

    init {
        // No served episode and no failures unless a test says so, so lastGeneratedAt is the fallback.
        every { podcastService.findLatestServedWindowEnd(any(), any()) } returns null
        every { podcastService.findFailedForSlot(any(), any()) } returns emptyList()
    }

    private fun duePodcast(requireReview: Boolean = false) = Podcast(
        id = "p1", userId = "u1", name = "Test", topic = "tech",
        cron = "0 0 0 * * *",
        requireReview = requireReview,
        lastGeneratedAt = "2026-02-22T00:00:00Z"
    )

    private fun schedulerWithClock(instant: Instant): BriefingGenerationScheduler {
        val clock = Clock.fixed(instant, ZoneOffset.UTC)
        return BriefingGenerationScheduler(
            podcastService, sourcePollingScheduler, sourceService, episodeWindowResolver, appProperties, clock
        )
    }

    // --- A slot is served by an episode whose window ends at it ----------------------------------

    private val slot = Instant.parse("2026-02-23T00:00:00Z")

    private fun generates(podcast: Podcast) {
        val episode = Episode(id = 10, podcastId = "p1", generatedAt = defaultNow.toString(), scriptText = "Script")
        coEvery { podcastService.generateBriefing(podcast, any()) } returns GenerateBriefingResult(episode = episode)
    }

    @Test
    fun `a slot that a re-run serves is not generated again, whatever lastGeneratedAt says`() {
        // Episode 296 was discarded and its window re-run as 297; lastGeneratedAt still pointed at
        // the day before, and the scheduler generated the slot a second time as 298.
        val podcast = duePodcast()
        every { podcastService.findAll() } returns listOf(podcast)
        every { podcastService.findLatestServedWindowEnd(podcast, defaultNow) } returns slot

        runBlocking { scheduler.checkAndGenerate() }

        coVerify(exactly = 0) { podcastService.generateBriefing(any(), any()) }
    }

    @Test
    fun `a discarded slot is due again, even when lastGeneratedAt says it was served`() {
        val podcast = duePodcast().copy(lastGeneratedAt = "2026-02-23T00:01:00Z")
        every { podcastService.findAll() } returns listOf(podcast)
        every { podcastService.findLatestServedWindowEnd(podcast, defaultNow) } returns Instant.parse("2026-02-22T00:00:00Z")
        generates(podcast)

        runBlocking { scheduler.checkAndGenerate() }

        verify { episodeWindowResolver.resolve(podcast, slot) }
        coVerify { podcastService.generateBriefing(podcast, any()) }
    }

    @Test
    fun `missed days generate only today's slot`() {
        val podcast = duePodcast()
        every { podcastService.findAll() } returns listOf(podcast)
        every { podcastService.findLatestServedWindowEnd(podcast, defaultNow) } returns Instant.parse("2026-02-19T00:00:00Z")
        generates(podcast)

        runBlocking { scheduler.checkAndGenerate() }

        verify(exactly = 1) { episodeWindowResolver.resolve(podcast, any()) }
        verify { episodeWindowResolver.resolve(podcast, slot) }
    }

    // --- A failed slot is retried once -------------------------------------------------------------

    private fun failed(id: Long, failedAt: String?) =
        Episode(id = id, podcastId = "p1", generatedAt = "2026-02-23T00:00:30Z", scriptText = "", status = EpisodeStatus.FAILED, failedAt = failedAt)

    @Test
    fun `a slot that failed 3 minutes ago is not retried yet`() {
        val podcast = duePodcast()
        every { podcastService.findAll() } returns listOf(podcast)
        every { podcastService.findFailedForSlot(podcast, slot) } returns listOf(failed(1, "2026-02-23T00:02:00Z"))

        runBlocking { scheduler.checkAndGenerate() }

        coVerify(exactly = 0) { podcastService.generateBriefing(any(), any()) }
    }

    @Test
    fun `a slot that failed more than 5 minutes ago is retried`() {
        val podcast = duePodcast()
        every { podcastService.findAll() } returns listOf(podcast)
        every { podcastService.findFailedForSlot(podcast, slot) } returns listOf(failed(1, "2026-02-23T00:00:00Z"))
        generates(podcast)

        runBlocking { scheduler.checkAndGenerate() }

        coVerify { podcastService.generateBriefing(podcast, any()) }
    }

    @Test
    fun `a slot that failed twice is not retried`() {
        val podcast = duePodcast()
        every { podcastService.findAll() } returns listOf(podcast)
        every { podcastService.findFailedForSlot(podcast, slot) } returns
            listOf(failed(1, "2026-02-22T23:00:00Z"), failed(2, "2026-02-22T23:30:00Z"))

        runBlocking { scheduler.checkAndGenerate() }

        coVerify(exactly = 0) { podcastService.generateBriefing(any(), any()) }
    }

    // --- Poll coverage gate ---------------------------------------------------------------------

    private fun behindSource(id: String = "s1", label: String? = "X via Narro") = Source(
        id = id, podcastId = "p1", type = SourceType.RSS, url = "https://example.com/$id", label = label
    )

    @Test
    fun `generation is deferred while a source has not polled the window`() {
        val podcast = duePodcast()
        every { podcastService.findAll() } returns listOf(podcast)
        every { sourceService.findSourcesBehindWindow("p1", any()) } returns listOf(behindSource())

        runBlocking { scheduler.checkAndGenerate() }

        // Nothing is created, so the slot stays due and the next tick re-checks.
        coVerify(exactly = 0) { podcastService.generateBriefing(any(), any()) }
    }

    @Test
    fun `generation proceeds once every source has polled the window`() {
        val podcast = duePodcast()
        val episode = Episode(id = 10, podcastId = "p1", generatedAt = Instant.now().toString(), scriptText = "Script")
        every { podcastService.findAll() } returns listOf(podcast)
        every { sourceService.findSourcesBehindWindow("p1", any()) } returns emptyList()
        coEvery { podcastService.generateBriefing(podcast, any()) } returns GenerateBriefingResult(episode = episode)

        runBlocking { scheduler.checkAndGenerate() }

        coVerify { podcastService.generateBriefing(podcast, any()) }
    }

    @Test
    fun `a source in backoff that will not poll before the deadline does not hold generation back`() {
        // Slot 00:00, deadline 00:30, now 00:05. Three failures at a 30-minute interval back the source
        // off to four hours, so waiting for it can only delay the episode.
        val podcast = duePodcast()
        val episode = Episode(id = 10, podcastId = "p1", generatedAt = Instant.now().toString(), scriptText = "Script")
        val backedOff = behindSource().copy(
            pollIntervalMinutes = 30, consecutiveFailures = 3, lastPolled = "2026-02-22T23:55:00Z"
        )
        every { podcastService.findAll() } returns listOf(podcast)
        every { sourceService.findSourcesBehindWindow("p1", any()) } returns listOf(backedOff)
        coEvery { podcastService.generateBriefing(podcast, any()) } returns GenerateBriefingResult(episode = episode)

        runBlocking { scheduler.checkAndGenerate() }

        coVerify { podcastService.generateBriefing(podcast, any()) }
    }

    @Test
    fun `a source that will poll again before the deadline still holds generation back`() {
        // One failure at a 5-minute interval doubles to 10 minutes: due at 00:10, inside the deadline.
        val podcast = duePodcast()
        val retrying = behindSource().copy(
            pollIntervalMinutes = 5, consecutiveFailures = 1, lastPolled = "2026-02-23T00:00:00Z"
        )
        every { podcastService.findAll() } returns listOf(podcast)
        every { sourceService.findSourcesBehindWindow("p1", any()) } returns listOf(retrying)

        runBlocking { scheduler.checkAndGenerate() }

        coVerify(exactly = 0) { podcastService.generateBriefing(any(), any()) }
    }

    @Test
    fun `one source worth waiting for holds generation back even when another is in backoff`() {
        val podcast = duePodcast()
        val backedOff = behindSource("s1").copy(
            pollIntervalMinutes = 30, consecutiveFailures = 3, lastPolled = "2026-02-22T23:55:00Z"
        )
        val neverPolled = behindSource("s2")
        every { podcastService.findAll() } returns listOf(podcast)
        every { sourceService.findSourcesBehindWindow("p1", any()) } returns listOf(backedOff, neverPolled)

        runBlocking { scheduler.checkAndGenerate() }

        coVerify(exactly = 0) { podcastService.generateBriefing(any(), any()) }
    }

    @Test
    fun `generation goes ahead after the coverage deadline even with a source behind`() {
        // The cron slot is midnight and the deadline is 30 minutes; it is now 00:45.
        val podcast = duePodcast()
        val episode = Episode(id = 10, podcastId = "p1", generatedAt = Instant.now().toString(), scriptText = "Script")
        every { podcastService.findAll() } returns listOf(podcast)
        every { sourceService.findSourcesBehindWindow("p1", any()) } returns listOf(behindSource())
        coEvery { podcastService.generateBriefing(podcast, any()) } returns GenerateBriefingResult(episode = episode)

        runBlocking { schedulerWithClock(Instant.parse("2026-02-23T00:45:00Z")).checkAndGenerate() }

        coVerify { podcastService.generateBriefing(podcast, any()) }
    }

    @Test
    fun `the window ends at the slot rather than at the moment the run starts`() {
        // Cron slot 00:00, run starting at 00:05: the episode still covers the period the slot
        // stands for, so a late start does not shift the window.
        val podcast = duePodcast()
        val episode = Episode(id = 10, podcastId = "p1", generatedAt = Instant.now().toString(), scriptText = "Script")
        every { podcastService.findAll() } returns listOf(podcast)
        coEvery { podcastService.generateBriefing(podcast, any()) } returns GenerateBriefingResult(episode = episode)

        runBlocking { scheduler.checkAndGenerate() }

        verify { episodeWindowResolver.resolve(podcast, Instant.parse("2026-02-23T00:00:00Z")) }
    }

    @Test
    fun `skips generation when podcastService returns null`() {
        val podcast = duePodcast(requireReview = true)
        every { podcastService.findAll() } returns listOf(podcast)
        coEvery { podcastService.generateBriefing(podcast, any()) } returns GenerateBriefingResult(episode = null)

        runBlocking { scheduler.checkAndGenerate() }

        coVerify { podcastService.generateBriefing(podcast, any()) }
    }

    @Test
    fun `delegates to PodcastService when due`() {
        val podcast = duePodcast(requireReview = false)
        val episode = Episode(id = 10, podcastId = "p1", generatedAt = Instant.now().toString(), scriptText = "Generated script")

        every { podcastService.findAll() } returns listOf(podcast)
        coEvery { podcastService.generateBriefing(podcast, any()) } returns GenerateBriefingResult(episode = episode)

        runBlocking { scheduler.checkAndGenerate() }

        coVerify { podcastService.generateBriefing(podcast, any()) }
    }

    @Test
    fun `delegates to PodcastService for review episode`() {
        val podcast = duePodcast(requireReview = true)
        val episode = Episode(id = 7, podcastId = "p1", generatedAt = Instant.now().toString(), scriptText = "Script", status = EpisodeStatus.PENDING_REVIEW)

        every { podcastService.findAll() } returns listOf(podcast)
        coEvery { podcastService.generateBriefing(podcast, any()) } returns GenerateBriefingResult(episode = episode)

        runBlocking { scheduler.checkAndGenerate() }

        coVerify { podcastService.generateBriefing(podcast, any()) }
    }

    @Test
    fun `handles null result from generateBriefing`() {
        val podcast = duePodcast(requireReview = false)
        every { podcastService.findAll() } returns listOf(podcast)
        coEvery { podcastService.generateBriefing(podcast, any()) } returns GenerateBriefingResult(episode = null)

        runBlocking { scheduler.checkAndGenerate() }

        coVerify { podcastService.generateBriefing(podcast, any()) }
    }

    @Test
    fun `same-day trigger fires minutes after scheduled time`() {
        // Cron: daily at 15:00. Now: 15:10 (10 min past trigger, same day)
        val now = Instant.parse("2026-02-23T15:10:00Z")
        val podcast = Podcast(
            id = "p1", userId = "u1", name = "Test", topic = "tech",
            cron = "0 0 15 * * *",
            lastGeneratedAt = "2026-02-22T15:00:00Z"
        )
        val episode = Episode(id = 1, podcastId = "p1", generatedAt = now.toString(), scriptText = "Script")

        every { podcastService.findAll() } returns listOf(podcast)
        coEvery { podcastService.generateBriefing(podcast, any()) } returns GenerateBriefingResult(episode = episode)

        runBlocking { schedulerWithClock(now).checkAndGenerate() }

        coVerify { podcastService.generateBriefing(podcast, any()) }
    }

    @Test
    fun `same-day catch-up fires hours after scheduled time`() {
        // Cron: daily at 15:00. Now: 18:00 (3 hours past trigger, same day)
        val now = Instant.parse("2026-02-23T18:00:00Z")
        val podcast = Podcast(
            id = "p1", userId = "u1", name = "Test", topic = "tech",
            cron = "0 0 15 * * *",
            lastGeneratedAt = "2026-02-22T15:00:00Z"
        )
        val episode = Episode(id = 1, podcastId = "p1", generatedAt = now.toString(), scriptText = "Script")

        every { podcastService.findAll() } returns listOf(podcast)
        coEvery { podcastService.generateBriefing(podcast, any()) } returns GenerateBriefingResult(episode = episode)

        runBlocking { schedulerWithClock(now).checkAndGenerate() }

        coVerify { podcastService.generateBriefing(podcast, any()) }
    }

    @Test
    fun `previous-day triggers are skipped`() {
        // Cron: daily at 15:00. Last generated 3 days ago. Now: 10:00 (all 3 past triggers are on previous days)
        val now = Instant.parse("2026-02-23T10:00:00Z")
        val podcast = Podcast(
            id = "p1", userId = "u1", name = "Test", topic = "tech",
            cron = "0 0 15 * * *",
            lastGeneratedAt = "2026-02-19T15:00:00Z"
        )

        every { podcastService.findAll() } returns listOf(podcast)

        runBlocking { schedulerWithClock(now).checkAndGenerate() }

        // All 3 missed triggers (Feb 20, 21, 22) are on previous days, today's (Feb 23 15:00) is in the future
        coVerify(exactly = 0) { podcastService.generateBriefing(any(), any()) }
    }

    @Test
    fun `skipped triggers do not call generateBriefing`() {
        // Cron: daily at 15:00. Now: next day at 10:00 (trigger was yesterday)
        val now = Instant.parse("2026-02-24T10:00:00Z")
        val podcast = Podcast(
            id = "p1", userId = "u1", name = "Test", topic = "tech",
            cron = "0 0 15 * * *",
            lastGeneratedAt = "2026-02-22T15:00:00Z"
        )

        every { podcastService.findAll() } returns listOf(podcast)

        runBlocking { schedulerWithClock(now).checkAndGenerate() }

        // Feb 23 15:00 trigger is on a previous day (yesterday), today's (Feb 24 15:00) is in the future
        coVerify(exactly = 0) { podcastService.generateBriefing(any(), any()) }
    }

    @Test
    fun `timezone-aware cron triggers at correct local time`() {
        // Cron: daily at 06:00. Timezone: Europe/Amsterdam (CET = UTC+1 in winter).
        // 06:00 CET = 05:00 UTC. Clock is at 05:05 UTC = 06:05 CET.
        val now = Instant.parse("2026-02-23T05:05:00Z")
        val podcast = Podcast(
            id = "p1", userId = "u1", name = "Test", topic = "tech",
            cron = "0 0 6 * * *",
            timezone = "Europe/Amsterdam",
            lastGeneratedAt = "2026-02-22T05:00:00Z"
        )
        val episode = Episode(id = 1, podcastId = "p1", generatedAt = now.toString(), scriptText = "Script")

        every { podcastService.findAll() } returns listOf(podcast)
        coEvery { podcastService.generateBriefing(podcast, any()) } returns GenerateBriefingResult(episode = episode)

        runBlocking { schedulerWithClock(now).checkAndGenerate() }

        coVerify { podcastService.generateBriefing(podcast, any()) }
    }

    @Test
    fun `timezone-aware cron does not trigger before local time`() {
        // Cron: daily at 06:00. Timezone: Europe/Amsterdam (CET = UTC+1 in winter).
        // 06:00 CET = 05:00 UTC. Clock is at 04:50 UTC = 05:50 CET (before 06:00 CET).
        val now = Instant.parse("2026-02-23T04:50:00Z")
        val podcast = Podcast(
            id = "p1", userId = "u1", name = "Test", topic = "tech",
            cron = "0 0 6 * * *",
            timezone = "Europe/Amsterdam",
            lastGeneratedAt = "2026-02-22T05:00:00Z"
        )

        every { podcastService.findAll() } returns listOf(podcast)

        runBlocking { schedulerWithClock(now).checkAndGenerate() }

        coVerify(exactly = 0) { podcastService.generateBriefing(any(), any()) }
    }

    @Test
    fun `same-day catch-up respects timezone boundary`() {
        // Cron: daily at 23:00. Timezone: Europe/Amsterdam (CET = UTC+1 in winter).
        // 23:00 CET on Feb 22 = 22:00 UTC on Feb 22. Last generated Feb 21.
        // Now: 01:30 UTC on Feb 23 = 02:30 CET on Feb 23 (next day in Amsterdam).
        // The 23:00 CET trigger on Feb 22 is on a previous day in Amsterdam timezone.
        val now = Instant.parse("2026-02-23T01:30:00Z")
        val podcast = Podcast(
            id = "p1", userId = "u1", name = "Test", topic = "tech",
            cron = "0 0 23 * * *",
            timezone = "Europe/Amsterdam",
            lastGeneratedAt = "2026-02-21T22:00:00Z"
        )

        every { podcastService.findAll() } returns listOf(podcast)

        runBlocking { schedulerWithClock(now).checkAndGenerate() }

        // Feb 22 23:00 CET is yesterday in Amsterdam, today's 23:00 CET is in the future
        coVerify(exactly = 0) { podcastService.generateBriefing(any(), any()) }
    }

    @Test
    fun `stale polling triggers a catch-up poll before generating`() {
        val podcast = duePodcast(requireReview = false)
        val episode = Episode(id = 10, podcastId = "p1", generatedAt = defaultNow.toString(), scriptText = "Script")
        every { podcastService.findAll() } returns listOf(podcast)
        coEvery { podcastService.generateBriefing(podcast, any()) } returns GenerateBriefingResult(episode = episode)
        every { sourcePollingScheduler.lastPollRoundCompletedAt } returns null // no round completed → stale

        runBlocking { scheduler.checkAndGenerate() }

        coVerifyOrder {
            sourcePollingScheduler.pollPodcastSourcesNow("p1")
            podcastService.generateBriefing(podcast, any())
        }
    }

    @Test
    fun `fresh polling skips the catch-up poll`() {
        val podcast = duePodcast(requireReview = false)
        val episode = Episode(id = 10, podcastId = "p1", generatedAt = defaultNow.toString(), scriptText = "Script")
        every { podcastService.findAll() } returns listOf(podcast)
        coEvery { podcastService.generateBriefing(podcast, any()) } returns GenerateBriefingResult(episode = episode)
        // Last poll round completed 1 minute before now → within the 10-minute threshold
        every { sourcePollingScheduler.lastPollRoundCompletedAt } returns defaultNow.minusSeconds(60)

        runBlocking { scheduler.checkAndGenerate() }

        coVerify(exactly = 0) { sourcePollingScheduler.pollPodcastSourcesNow(any()) }
        coVerify { podcastService.generateBriefing(podcast, any()) }
    }

    @Test
    fun `newly created podcast triggers on first check same day`() {
        // Cron: daily at 06:00. No lastGeneratedAt. Now: 10:00 (same day, past trigger).
        val now = Instant.parse("2026-02-23T10:00:00Z")
        val podcast = Podcast(
            id = "p1", userId = "u1", name = "Test", topic = "tech",
            cron = "0 0 6 * * *",
            lastGeneratedAt = null
        )
        val episode = Episode(id = 1, podcastId = "p1", generatedAt = now.toString(), scriptText = "Script")

        every { podcastService.findAll() } returns listOf(podcast)
        coEvery { podcastService.generateBriefing(podcast, any()) } returns GenerateBriefingResult(episode = episode)

        runBlocking { schedulerWithClock(now).checkAndGenerate() }

        coVerify { podcastService.generateBriefing(podcast, any()) }
    }
}
