package com.aisummarypodcast.scheduler

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.BriefingProperties
import com.aisummarypodcast.config.EncryptionProperties
import com.aisummarypodcast.config.EpisodesProperties
import com.aisummarypodcast.config.FeedProperties
import com.aisummarypodcast.config.LlmProperties
import com.aisummarypodcast.config.SourceProperties
import com.aisummarypodcast.podcast.PodcastService
import com.aisummarypodcast.source.SourceHostBreaker
import com.aisummarypodcast.testCircuitBreakerRegistry
import com.aisummarypodcast.source.SourcePoller
import com.aisummarypodcast.source.SourceService
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.Source
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
    private val sourceService = mockk<SourceService>(relaxed = true) {
        // The poll guard re-reads each source; it is what the test listed.
        every { findById(any()) } answers { findEnabled().find { it.id == firstArg<String>() } }
    }
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
            sourcePoller, sourceService,
            props, podcastService, PollDelayResolver(props), SourceHostBreaker(testCircuitBreakerRegistry())
        )
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
        every { sourceService.findEnabled() } returns listOf(source)

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
        every { sourceService.findEnabled() } returns listOf(source)
        every { podcastService.findById("p1") } returns podcast

        scheduler().pollSources()

        verify { sourcePoller.poll(source, null, 7, listOf("s1")) }
    }
}
