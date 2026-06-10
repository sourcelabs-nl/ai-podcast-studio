package com.aisummarypodcast.scheduler

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.BriefingProperties
import com.aisummarypodcast.config.EncryptionProperties
import com.aisummarypodcast.config.EpisodesProperties
import com.aisummarypodcast.config.FeedProperties
import com.aisummarypodcast.config.LlmProperties
import com.aisummarypodcast.config.SourceProperties
import com.aisummarypodcast.podcast.PodcastService
import com.aisummarypodcast.source.SourcePoller
import com.aisummarypodcast.source.SourceService
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.Source
import com.aisummarypodcast.store.SourceRepository
import com.aisummarypodcast.store.SourceType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class SourcePollingSchedulerBackoffTest {

    private val sourcePoller = mockk<SourcePoller>(relaxed = true)
    private val sourceRepository = mockk<SourceRepository>()
    private val sourceService = mockk<SourceService>(relaxed = true)
    private val podcastService = mockk<PodcastService>()

    private fun appProperties(maxBackoffHours: Int = 24) = AppProperties(
        llm = LlmProperties(),
        briefing = BriefingProperties(),
        episodes = EpisodesProperties(),
        feed = FeedProperties(),
        encryption = EncryptionProperties(masterKey = "test-key"),
        source = SourceProperties(maxBackoffHours = maxBackoffHours)
    )

    private fun scheduler(maxBackoffHours: Int = 24): SourcePollingScheduler {
        val props = appProperties(maxBackoffHours)
        return SourcePollingScheduler(
            sourcePoller, sourceRepository, sourceService,
            props, podcastService, PollDelayResolver(props)
        )
    }

    @Test
    fun `effectivePollIntervalMinutes returns normal interval when no failures`() {
        val source = Source(id = "s1", podcastId = "p1", type = SourceType.RSS, url = "u", pollIntervalMinutes = 60, consecutiveFailures = 0)
        assertEquals(60L, scheduler().effectivePollIntervalMinutes(source))
    }

    @Test
    fun `effectivePollIntervalMinutes doubles on first failure`() {
        val source = Source(id = "s1", podcastId = "p1", type = SourceType.RSS, url = "u", pollIntervalMinutes = 60, consecutiveFailures = 1)
        assertEquals(120L, scheduler().effectivePollIntervalMinutes(source))
    }

    @Test
    fun `effectivePollIntervalMinutes is 8x on third failure`() {
        val source = Source(id = "s1", podcastId = "p1", type = SourceType.RSS, url = "u", pollIntervalMinutes = 60, consecutiveFailures = 3)
        assertEquals(480L, scheduler().effectivePollIntervalMinutes(source))
    }

    @Test
    fun `effectivePollIntervalMinutes is capped at maxBackoffHours`() {
        val source = Source(id = "s1", podcastId = "p1", type = SourceType.RSS, url = "u", pollIntervalMinutes = 60, consecutiveFailures = 10)
        assertEquals(1440L, scheduler(maxBackoffHours = 24).effectivePollIntervalMinutes(source))
    }

    @Test
    fun `effectivePollIntervalMinutes respects custom maxBackoffHours`() {
        val source = Source(id = "s1", podcastId = "p1", type = SourceType.RSS, url = "u", pollIntervalMinutes = 60, consecutiveFailures = 10)
        assertEquals(360L, scheduler(maxBackoffHours = 6).effectivePollIntervalMinutes(source))
    }

    private val podcast = Podcast(id = "p1", userId = "u1", name = "Test", topic = "tech")

    @Test
    fun `source with failures is skipped when backoff interval has not elapsed`() = runTest {
        val source = Source(
            id = "s1", podcastId = "p1", type = SourceType.RSS, url = "u",
            pollIntervalMinutes = 60, consecutiveFailures = 2,
            lastPolled = Instant.now().minus(100, ChronoUnit.MINUTES).toString(),
            enabled = true
        )
        every { sourceRepository.findAll() } returns listOf(source)

        scheduler().pollSources()

        verify(exactly = 0) { sourcePoller.poll(any(), any(), any(), any()) }
    }

    @Test
    fun `source with failures is polled when backoff interval has elapsed`() = runTest {
        val source = Source(
            id = "s1", podcastId = "p1", type = SourceType.RSS, url = "u",
            pollIntervalMinutes = 60, consecutiveFailures = 1,
            lastPolled = Instant.now().minus(130, ChronoUnit.MINUTES).toString(),
            enabled = true
        )
        every { sourceRepository.findAll() } returns listOf(source)
        every { podcastService.findById("p1") } returns podcast

        scheduler().pollSources()

        verify { sourcePoller.poll(source, null, 7, listOf("s1")) }
    }

    @Test
    fun `per-source maxBackoffHours override is respected`() {
        val source = Source(
            id = "s1", podcastId = "p1", type = SourceType.RSS, url = "u",
            pollIntervalMinutes = 60, consecutiveFailures = 10,
            maxBackoffHours = 48
        )
        assertEquals(2880L, scheduler(maxBackoffHours = 24).effectivePollIntervalMinutes(source))
    }
}
