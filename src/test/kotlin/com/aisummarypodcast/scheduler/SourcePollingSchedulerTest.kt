package com.aisummarypodcast.scheduler

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.BriefingProperties
import com.aisummarypodcast.config.EncryptionProperties
import com.aisummarypodcast.config.EpisodesProperties
import com.aisummarypodcast.config.FeedProperties
import com.aisummarypodcast.config.HostOverride
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
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class SourcePollingSchedulerTest {

    private val sourcePoller = mockk<SourcePoller>(relaxed = true)
    private val sourceRepository = mockk<SourceRepository> {
        every { findAll() } returns emptyList()
    }
    private val sourceService = mockk<SourceService>(relaxed = true)
    private val podcastService = mockk<PodcastService> {
        every { scoreReadySources(any()) } returns Unit
    }

    private val podcast = Podcast(id = "p1", userId = "owner-1", name = "Test", topic = "tech")

    private fun appProperties(
        maxArticleAgeDays: Int = 7,
        pollDelaySeconds: Map<String, Int> = emptyMap(),
        hostOverrides: Map<String, HostOverride> = emptyMap()
    ) = AppProperties(
        llm = LlmProperties(),
        briefing = BriefingProperties(),
        episodes = EpisodesProperties(),
        feed = FeedProperties(),
        encryption = EncryptionProperties(masterKey = "test-key"),
        source = SourceProperties(
            maxArticleAgeDays = maxArticleAgeDays,
            pollDelaySeconds = pollDelaySeconds,
            hostOverrides = hostOverrides
        )
    )

    private fun scheduler(
        maxArticleAgeDays: Int = 7,
        pollDelaySeconds: Map<String, Int> = emptyMap(),
        hostOverrides: Map<String, HostOverride> = emptyMap()
    ): SourcePollingScheduler {
        val props = appProperties(maxArticleAgeDays, pollDelaySeconds, hostOverrides)
        return SourcePollingScheduler(
            sourcePoller, sourceRepository, sourceService,
            props, podcastService, PollDelayResolver(props)
        )
    }

    @Test
    fun `delegates old article and post cleanup to SourceService before polling`() = runTest {
        scheduler(maxArticleAgeDays = 7).pollSources()

        verify { sourceService.cleanupOldArticlesAndPosts(7) }
    }

    @Test
    fun `resolves podcast owner userId for twitter source`() = runTest {
        val twitterSource = Source(
            id = "s1", podcastId = "p1", type = SourceType.TWITTER, url = "testuser",
            lastPolled = Instant.now().minus(2, ChronoUnit.HOURS).toString(), enabled = true
        )

        every { sourceRepository.findAll() } returns listOf(twitterSource)
        every { podcastService.findById("p1") } returns podcast

        scheduler().pollSources()

        verify { podcastService.findById("p1") }
        verify { sourcePoller.poll(twitterSource, "owner-1", 7, listOf("s1")) }
    }

    @Test
    fun `does not resolve userId for rss source`() = runTest {
        val rssSource = Source(
            id = "s2", podcastId = "p1", type = SourceType.RSS, url = "https://example.com/feed",
            lastPolled = Instant.now().minus(2, ChronoUnit.HOURS).toString(), enabled = true
        )

        every { sourceRepository.findAll() } returns listOf(rssSource)
        every { podcastService.findById("p1") } returns podcast

        scheduler().pollSources()

        verify { sourcePoller.poll(rssSource, null, 7, listOf("s2")) }
    }

    // --- 5.1 Startup jitter tests ---

    @Test
    fun `applyStartupJitter sets synthetic lastPolled for sources with null lastPolled`() = runTest {
        val source = Source(id = "s1", podcastId = "p1", type = SourceType.RSS, url = "https://example.com/rss", pollIntervalMinutes = 60)
        val saved = slot<Source>()
        every { sourceRepository.save(capture(saved)) } answers { saved.captured }

        val result = scheduler().applyStartupJitter(listOf(source))

        assertEquals(1, result.size)
        assertNotNull(result[0].lastPolled)
        val syntheticLastPolled = Instant.parse(result[0].lastPolled!!)
        val now = Instant.now()
        assertTrue(syntheticLastPolled.isBefore(now.plusSeconds(1)))
        assertTrue(syntheticLastPolled.isAfter(now.minus(61, ChronoUnit.MINUTES)))
        verify(exactly = 1) { sourceRepository.save(any()) }
    }

    @Test
    fun `newly added source with null lastPolled is polled on next cycle without jitter`() = runTest {
        val existing = Source(
            id = "s1", podcastId = "p1", type = SourceType.RSS, url = "https://example.com/rss",
            lastPolled = Instant.now().minus(2, ChronoUnit.HOURS).toString()
        )
        val newlyAdded = Source(
            id = "s2", podcastId = "p1", type = SourceType.RSS, url = "https://other.com/rss",
            lastPolled = null, pollIntervalMinutes = 60
        )
        every { podcastService.findById("p1") } returns podcast
        every { sourceRepository.save(any()) } answers { firstArg() }

        val scheduler = scheduler()

        // First cycle: only the existing source is present, jitter applied to it would be a no-op (already has lastPolled).
        every { sourceRepository.findAll() } returns listOf(existing)
        scheduler.pollSources()

        // Mid-run: a new source is added with lastPolled = null. It must be polled on the next cycle
        // without jitter consuming the first interval.
        every { sourceRepository.findAll() } returns listOf(existing, newlyAdded)
        scheduler.pollSources()

        verify(exactly = 1) { sourcePoller.poll(newlyAdded, null, 7, listOf("s1", "s2")) }
        verify(exactly = 0) { sourceRepository.save(match { it.id == "s2" }) }
    }

    @Test
    fun `applyStartupJitter does not modify sources with existing lastPolled`() = runTest {
        val existingLastPolled = Instant.now().minus(30, ChronoUnit.MINUTES).toString()
        val source = Source(
            id = "s1", podcastId = "p1", type = SourceType.RSS, url = "https://example.com/rss",
            lastPolled = existingLastPolled
        )

        val result = scheduler().applyStartupJitter(listOf(source))

        assertEquals(1, result.size)
        assertEquals(existingLastPolled, result[0].lastPolled)
        verify(exactly = 0) { sourceRepository.save(any()) }
    }

    // --- 5.2 Parallel host grouping tests ---

    @Test
    fun `sources are grouped by host and all get polled`() = runTest {
        val source1 = Source(
            id = "s1", podcastId = "p1", type = SourceType.RSS, url = "https://nitter.net/user1/rss",
            lastPolled = Instant.now().minus(2, ChronoUnit.HOURS).toString()
        )
        val source2 = Source(
            id = "s2", podcastId = "p1", type = SourceType.RSS, url = "https://nitter.net/user2/rss",
            lastPolled = Instant.now().minus(2, ChronoUnit.HOURS).toString()
        )
        val source3 = Source(
            id = "s3", podcastId = "p1", type = SourceType.RSS, url = "https://reddit.com/feed",
            lastPolled = Instant.now().minus(2, ChronoUnit.HOURS).toString()
        )
        every { sourceRepository.findAll() } returns listOf(source1, source2, source3)
        every { podcastService.findById("p1") } returns podcast

        scheduler().pollSources()

        verify(exactly = 1) { sourcePoller.poll(source1, null, 7, listOf("s1", "s2", "s3")) }
        verify(exactly = 1) { sourcePoller.poll(source2, null, 7, listOf("s1", "s2", "s3")) }
        verify(exactly = 1) { sourcePoller.poll(source3, null, 7, listOf("s1", "s2", "s3")) }
    }

    // --- 5.3 supervisorScope isolation tests ---

    @Test
    fun `one host group failure does not cancel other host groups`() = runTest {
        val source1 = Source(
            id = "s1", podcastId = "p1", type = SourceType.RSS, url = "https://failing-host.net/rss",
            lastPolled = Instant.now().minus(2, ChronoUnit.HOURS).toString()
        )
        val source2 = Source(
            id = "s2", podcastId = "p1", type = SourceType.RSS, url = "https://working-host.net/rss",
            lastPolled = Instant.now().minus(2, ChronoUnit.HOURS).toString()
        )
        every { sourceRepository.findAll() } returns listOf(source1, source2)
        every { podcastService.findById("p1") } returns podcast
        every { sourcePoller.poll(source1, null, 7, listOf("s1", "s2")) } throws RuntimeException("Host down")

        scheduler().pollSources()

        verify(exactly = 1) { sourcePoller.poll(source2, null, 7, listOf("s1", "s2")) }
    }

    // --- 5.4 Delay application tests ---

    @Test
    fun `per-source pollDelaySeconds overrides host override`() {
        val source = Source(
            id = "s1", podcastId = "p1", type = SourceType.RSS, url = "https://nitter.net/user/rss",
            pollDelaySeconds = 5
        )
        val props = appProperties(hostOverrides = mapOf("nitter.net" to HostOverride(pollDelaySeconds = 3)))
        val resolver = PollDelayResolver(props)
        assertEquals(5, resolver.resolveDelaySeconds(source))
    }

    @Test
    fun `host override delay is applied when no per-source delay`() {
        val source = Source(
            id = "s1", podcastId = "p1", type = SourceType.RSS, url = "https://nitter.net/user/rss"
        )
        val props = appProperties(hostOverrides = mapOf("nitter.net" to HostOverride(pollDelaySeconds = 3)))
        val resolver = PollDelayResolver(props)
        assertEquals(3, resolver.resolveDelaySeconds(source))
    }

    @Test
    fun `type default delay is applied when no per-source or host override`() {
        val source = Source(
            id = "s1", podcastId = "p1", type = SourceType.WEBSITE, url = "https://example.com"
        )
        val props = appProperties(pollDelaySeconds = mapOf("website" to 2))
        val resolver = PollDelayResolver(props)
        assertEquals(2, resolver.resolveDelaySeconds(source))
    }

    // --- Poll round tracking + on-demand catch-up poll ---

    @Test
    fun `records last poll round completion time after a round`() = runTest {
        val s = scheduler()
        assertEquals(null, s.lastPollRoundCompletedAt)

        s.pollSources()

        val completed = s.lastPollRoundCompletedAt
        assertNotNull(completed)
        assertTrue(completed!!.isAfter(Instant.now().minusSeconds(5)))
    }

    // --- Post-round eager ranking trigger ---

    @Test
    fun `triggers eager ranking once per due podcast after a round`() = runTest {
        val s1 = Source(
            id = "s1", podcastId = "p1", type = SourceType.RSS, url = "https://a.com/feed",
            lastPolled = Instant.now().minus(2, ChronoUnit.HOURS).toString(), enabled = true
        )
        val s2 = Source(
            id = "s2", podcastId = "p2", type = SourceType.RSS, url = "https://b.com/feed",
            lastPolled = Instant.now().minus(2, ChronoUnit.HOURS).toString(), enabled = true
        )
        val podcast2 = Podcast(id = "p2", userId = "owner-2", name = "Test 2", topic = "ai")
        every { sourceRepository.findAll() } returns listOf(s1, s2)
        every { podcastService.findById("p1") } returns podcast
        every { podcastService.findById("p2") } returns podcast2

        scheduler().pollSources()

        verify(exactly = 1) { podcastService.scoreReadySources(podcast) }
        verify(exactly = 1) { podcastService.scoreReadySources(podcast2) }
    }

    @Test
    fun `eager ranking failure for one podcast does not abort others`() = runTest {
        val s1 = Source(
            id = "s1", podcastId = "p1", type = SourceType.RSS, url = "https://a.com/feed",
            lastPolled = Instant.now().minus(2, ChronoUnit.HOURS).toString(), enabled = true
        )
        val s2 = Source(
            id = "s2", podcastId = "p2", type = SourceType.RSS, url = "https://b.com/feed",
            lastPolled = Instant.now().minus(2, ChronoUnit.HOURS).toString(), enabled = true
        )
        val podcast2 = Podcast(id = "p2", userId = "owner-2", name = "Test 2", topic = "ai")
        every { sourceRepository.findAll() } returns listOf(s1, s2)
        every { podcastService.findById("p1") } returns podcast
        every { podcastService.findById("p2") } returns podcast2
        every { podcastService.scoreReadySources(podcast) } throws RuntimeException("boom")

        scheduler().pollSources()

        verify(exactly = 1) { podcastService.scoreReadySources(podcast2) }
    }

    @Test
    fun `pollPodcastSourcesNow polls only the podcast's enabled sources`() = runTest {
        val rss = Source(
            id = "s2", podcastId = "p1", type = SourceType.RSS, url = "https://example.com/feed", enabled = true
        )
        val disabled = Source(
            id = "s3", podcastId = "p1", type = SourceType.RSS, url = "https://disabled.com/feed", enabled = false
        )
        every { sourceRepository.findByPodcastId("p1") } returns listOf(rss, disabled)
        every { podcastService.findById("p1") } returns podcast

        scheduler().pollPodcastSourcesNow("p1")

        verify { sourcePoller.poll(rss, null, 7, listOf("s2")) }
        verify(exactly = 0) { sourcePoller.poll(disabled, any(), any(), any()) }
    }
}
