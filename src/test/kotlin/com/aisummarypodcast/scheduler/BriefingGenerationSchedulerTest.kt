package com.aisummarypodcast.scheduler

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.SourceProperties
import com.aisummarypodcast.podcast.GenerateBriefingResult
import com.aisummarypodcast.podcast.PodcastService
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodeStatus
import com.aisummarypodcast.store.Podcast
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class BriefingGenerationSchedulerTest {

    private val podcastService = mockk<PodcastService>()
    // Relaxed: lastPollRoundCompletedAt defaults to null (stale), pollPodcastSourcesNow is a no-op.
    private val sourcePollingScheduler = mockk<SourcePollingScheduler>(relaxed = true)
    private val appProperties = mockk<AppProperties> { every { source } returns SourceProperties() }

    // Fixed clock at 00:05 UTC — 5 minutes after the midnight cron trigger (same day)
    private val defaultNow = Instant.parse("2026-02-23T00:05:00Z")
    private val defaultClock = Clock.fixed(defaultNow, ZoneOffset.UTC)

    private val scheduler = BriefingGenerationScheduler(
        podcastService, sourcePollingScheduler, appProperties, defaultClock
    )

    private fun duePodcast(requireReview: Boolean = false) = Podcast(
        id = "p1", userId = "u1", name = "Test", topic = "tech",
        cron = "0 0 0 * * *",
        requireReview = requireReview,
        lastGeneratedAt = "2026-02-22T00:00:00Z"
    )

    private fun schedulerWithClock(instant: Instant): BriefingGenerationScheduler {
        val clock = Clock.fixed(instant, ZoneOffset.UTC)
        return BriefingGenerationScheduler(podcastService, sourcePollingScheduler, appProperties, clock)
    }

    @Test
    fun `skips generation when podcastService returns null`() {
        val podcast = duePodcast(requireReview = true)
        every { podcastService.findAll() } returns listOf(podcast)
        coEvery { podcastService.generateBriefing(podcast) } returns GenerateBriefingResult(episode = null)

        runBlocking { scheduler.checkAndGenerate() }

        coVerify { podcastService.generateBriefing(podcast) }
    }

    @Test
    fun `delegates to PodcastService when due`() {
        val podcast = duePodcast(requireReview = false)
        val episode = Episode(id = 10, podcastId = "p1", generatedAt = Instant.now().toString(), scriptText = "Generated script")

        every { podcastService.findAll() } returns listOf(podcast)
        coEvery { podcastService.generateBriefing(podcast) } returns GenerateBriefingResult(episode = episode)

        runBlocking { scheduler.checkAndGenerate() }

        coVerify { podcastService.generateBriefing(podcast) }
    }

    @Test
    fun `delegates to PodcastService for review episode`() {
        val podcast = duePodcast(requireReview = true)
        val episode = Episode(id = 7, podcastId = "p1", generatedAt = Instant.now().toString(), scriptText = "Script", status = EpisodeStatus.PENDING_REVIEW)

        every { podcastService.findAll() } returns listOf(podcast)
        coEvery { podcastService.generateBriefing(podcast) } returns GenerateBriefingResult(episode = episode)

        runBlocking { scheduler.checkAndGenerate() }

        coVerify { podcastService.generateBriefing(podcast) }
    }

    @Test
    fun `handles null result from generateBriefing`() {
        val podcast = duePodcast(requireReview = false)
        every { podcastService.findAll() } returns listOf(podcast)
        coEvery { podcastService.generateBriefing(podcast) } returns GenerateBriefingResult(episode = null)

        runBlocking { scheduler.checkAndGenerate() }

        coVerify { podcastService.generateBriefing(podcast) }
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
        coEvery { podcastService.generateBriefing(podcast) } returns GenerateBriefingResult(episode = episode)

        runBlocking { schedulerWithClock(now).checkAndGenerate() }

        coVerify { podcastService.generateBriefing(podcast) }
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
        coEvery { podcastService.generateBriefing(podcast) } returns GenerateBriefingResult(episode = episode)

        runBlocking { schedulerWithClock(now).checkAndGenerate() }

        coVerify { podcastService.generateBriefing(podcast) }
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
        coVerify(exactly = 0) { podcastService.generateBriefing(any()) }
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
        coVerify(exactly = 0) { podcastService.generateBriefing(any()) }
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
        coEvery { podcastService.generateBriefing(podcast) } returns GenerateBriefingResult(episode = episode)

        runBlocking { schedulerWithClock(now).checkAndGenerate() }

        coVerify { podcastService.generateBriefing(podcast) }
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

        coVerify(exactly = 0) { podcastService.generateBriefing(any()) }
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
        coVerify(exactly = 0) { podcastService.generateBriefing(any()) }
    }

    @Test
    fun `stale polling triggers a catch-up poll before generating`() {
        val podcast = duePodcast(requireReview = false)
        val episode = Episode(id = 10, podcastId = "p1", generatedAt = defaultNow.toString(), scriptText = "Script")
        every { podcastService.findAll() } returns listOf(podcast)
        coEvery { podcastService.generateBriefing(podcast) } returns GenerateBriefingResult(episode = episode)
        every { sourcePollingScheduler.lastPollRoundCompletedAt } returns null // no round completed → stale

        runBlocking { scheduler.checkAndGenerate() }

        coVerifyOrder {
            sourcePollingScheduler.pollPodcastSourcesNow("p1")
            podcastService.generateBriefing(podcast)
        }
    }

    @Test
    fun `fresh polling skips the catch-up poll`() {
        val podcast = duePodcast(requireReview = false)
        val episode = Episode(id = 10, podcastId = "p1", generatedAt = defaultNow.toString(), scriptText = "Script")
        every { podcastService.findAll() } returns listOf(podcast)
        coEvery { podcastService.generateBriefing(podcast) } returns GenerateBriefingResult(episode = episode)
        // Last poll round completed 1 minute before now → within the 10-minute threshold
        every { sourcePollingScheduler.lastPollRoundCompletedAt } returns defaultNow.minusSeconds(60)

        runBlocking { scheduler.checkAndGenerate() }

        coVerify(exactly = 0) { sourcePollingScheduler.pollPodcastSourcesNow(any()) }
        coVerify { podcastService.generateBriefing(podcast) }
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
        coEvery { podcastService.generateBriefing(podcast) } returns GenerateBriefingResult(episode = episode)

        runBlocking { schedulerWithClock(now).checkAndGenerate() }

        coVerify { podcastService.generateBriefing(podcast) }
    }
}
