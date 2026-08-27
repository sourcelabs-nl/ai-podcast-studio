package com.aisummarypodcast.source

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.openBreakerFor
import com.aisummarypodcast.testCircuitBreakerRegistry
import com.aisummarypodcast.config.BriefingProperties
import com.aisummarypodcast.config.EncryptionProperties
import com.aisummarypodcast.config.EpisodesProperties
import com.aisummarypodcast.config.FeedProperties
import com.aisummarypodcast.config.LlmProperties
import com.aisummarypodcast.config.SourceProperties
import com.aisummarypodcast.store.ArticleRepository
import com.aisummarypodcast.store.Post
import com.aisummarypodcast.store.PostRepository
import com.aisummarypodcast.store.Source
import com.aisummarypodcast.store.SourceRepository
import com.aisummarypodcast.store.SourceType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional

class SourceServiceTest {

    private val sourceSlot = slot<Source>()
    private val sourceRepository = mockk<SourceRepository> {
        every { save(capture(sourceSlot)) } answers { firstArg() }
    }
    private val articleRepository = mockk<ArticleRepository>()
    private val postRepository = mockk<PostRepository>()
    private val rssFeedFetcher = mockk<RssFeedFetcher>()
    private val websiteFetcher = mockk<WebsiteFetcher>()
    private val appProperties = AppProperties(
        llm = LlmProperties(),
        briefing = BriefingProperties(),
        episodes = EpisodesProperties(),
        feed = FeedProperties(),
        encryption = EncryptionProperties(masterKey = "test-key"),
        source = SourceProperties()
    )
    private val circuitBreakerRegistry = testCircuitBreakerRegistry()
    private val sourceHostBreaker = SourceHostBreaker(circuitBreakerRegistry)

    private val service = SourceService(
        sourceRepository, articleRepository, postRepository, rssFeedFetcher, websiteFetcher, sourceHostBreaker
    )

    private fun source(id: String, url: String, failures: Int = 0, failureType: String? = null) = Source(
        id = id, podcastId = "p1", type = SourceType.RSS, url = url,
        consecutiveFailures = failures, lastFailureType = failureType
    )

    @Test
    fun `resetFailureState clears counters only for sources that have failures`() {
        val saved = mutableListOf<Source>()
        every { sourceRepository.save(any()) } answers { saved.add(firstArg()); firstArg() }
        val failing = source("s1", "https://nitter.net/a/rss", failures = 8, failureType = "permanent")
        val healthy = source("s2", "https://nitter.net/b/rss")

        service.resetFailureState(listOf(failing, healthy))

        assertEquals(listOf("s1"), saved.map { it.id })
        assertEquals(0, saved.single().consecutiveFailures)
        assertNull(saved.single().lastFailureType)
    }

    @Test
    fun `getHostBreakerStates reports an open breaker for a structurally down host`() {
        val dead = (1..3).map { source("s$it", "https://nitter.net/$it/rss", failures = 5, failureType = "permanent") }
        every { sourceRepository.findAll() } returns dead
        circuitBreakerRegistry.openBreakerFor("nitter.net")

        val states = service.getHostBreakerStates(dead)

        assertTrue(states.getValue("s1").open)
        assertEquals("nitter.net", states.getValue("s1").host)
        assertEquals(3, states.getValue("s1").sourceCount)
    }

    @Test
    fun `getHostBreakerStates reports a closed breaker for a healthy host`() {
        val healthy = (1..3).map { source("s$it", "https://example.com/$it/rss") }
        every { sourceRepository.findAll() } returns healthy

        val states = service.getHostBreakerStates(healthy)

        assertFalse(states.getValue("s1").open)
    }

    @Test
    fun `cleanupOldArticlesAndPosts deletes both with a cutoff computed from maxArticleAgeDays`() {
        every { articleRepository.deleteOldUnprocessedArticles(any()) } returns Unit
        every { postRepository.deleteOldUnlinkedPosts(any()) } returns Unit

        service.cleanupOldArticlesAndPosts(7)

        val cutoffMatches: (String) -> Boolean = { cutoff ->
            val parsed = Instant.parse(cutoff)
            val expected = Instant.now().minus(7, ChronoUnit.DAYS)
            parsed.isAfter(expected.minusSeconds(5)) && parsed.isBefore(expected.plusSeconds(5))
        }
        verify { articleRepository.deleteOldUnprocessedArticles(match(cutoffMatches)) }
        verify { postRepository.deleteOldUnlinkedPosts(match(cutoffMatches)) }
    }

    @Test
    fun `re-enabling a disabled source clears failure tracking`() {
        val disabledSource = Source(
            id = "s1", podcastId = "p1", type = SourceType.RSS, url = "https://example.com/feed",
            enabled = false, consecutiveFailures = 5, lastFailureType = "permanent",
            disabledReason = "Auto-disabled after 5 consecutive HTTP 404 Not Found errors"
        )
        every { sourceRepository.findById("s1") } returns Optional.of(disabledSource)

        service.update("s1", SourceType.RSS, "https://example.com/feed", SourceConfig(pollIntervalMinutes = 60, enabled = true))

        assertTrue(sourceSlot.captured.enabled)
        assertEquals(0, sourceSlot.captured.consecutiveFailures)
        assertNull(sourceSlot.captured.lastFailureType)
        assertNull(sourceSlot.captured.disabledReason)
    }

    @Test
    fun `updating an already-enabled source does not reset failure tracking`() {
        val source = Source(
            id = "s1", podcastId = "p1", type = SourceType.RSS, url = "https://example.com/feed",
            enabled = true, consecutiveFailures = 2, lastFailureType = "transient"
        )
        every { sourceRepository.findById("s1") } returns Optional.of(source)

        service.update("s1", SourceType.RSS, "https://example.com/new-feed", SourceConfig(pollIntervalMinutes = 60, enabled = true))

        assertEquals(2, sourceSlot.captured.consecutiveFailures)
        assertEquals("transient", sourceSlot.captured.lastFailureType)
    }

    @Test
    fun `getArticleCounts returns counts grouped by source`() {
        every { articleRepository.getArticleCountsBySourceIds(listOf("s1", "s2"), 5) } returns mapOf(
            "s1" to SourceArticleCounts("s1", 42, 18),
            "s2" to SourceArticleCounts("s2", 10, 0)
        )

        val result = service.getArticleCounts(listOf("s1", "s2"), 5)

        assertEquals(2, result.size)
        assertEquals(42, result["s1"]?.total)
        assertEquals(18, result["s1"]?.relevant)
        assertEquals(10, result["s2"]?.total)
        assertEquals(0, result["s2"]?.relevant)
    }

    @Test
    fun `getArticleCounts returns empty map for empty source list`() {
        every { articleRepository.getArticleCountsBySourceIds(emptyList(), 5) } returns emptyMap()

        val result = service.getArticleCounts(emptyList(), 5)
        assertEquals(emptyMap<String, SourceArticleCounts>(), result)
    }

    @Test
    fun `getArticleCounts returns empty map when no articles exist`() {
        every { articleRepository.getArticleCountsBySourceIds(listOf("s1"), 5) } returns emptyMap()

        val result = service.getArticleCounts(listOf("s1"), 5)
        assertEquals(emptyMap<String, SourceArticleCounts>(), result)
    }

    // --- URL validation tests ---

    private fun post(title: String = "Test", body: String = "Content") = Post(
        sourceId = "validation", title = title, body = body, url = "https://example.com",
        contentHash = "hash", createdAt = ""
    )

    @Test
    fun `validateUrl passes for RSS source with items`() {
        every { rssFeedFetcher.fetch("https://example.com/feed", "validation", null, null, deepFetch = false) } returns listOf(post())

        service.validateUrl(SourceType.RSS, "https://example.com/feed")
    }

    @Test
    fun `validateUrl throws for RSS source with no items`() {
        every { rssFeedFetcher.fetch("https://example.com/feed", "validation", null, null, deepFetch = false) } returns emptyList()

        val ex = assertThrows<IllegalArgumentException> {
            service.validateUrl(SourceType.RSS, "https://example.com/feed")
        }
        assertTrue(ex.message!!.contains("returned no items"))
    }

    @Test
    fun `validateUrl throws for RSS source with fetch error`() {
        every { rssFeedFetcher.fetch("https://bad.com/feed", "validation", null, null, deepFetch = false) } throws RuntimeException("Connection refused")

        val ex = assertThrows<IllegalArgumentException> {
            service.validateUrl(SourceType.RSS, "https://bad.com/feed")
        }
        assertTrue(ex.message!!.contains("Could not reach URL"))
    }

    @Test
    fun `validateUrl throws for RSS source with invalid XML`() {
        every { rssFeedFetcher.fetch("https://bad.com/html", "validation", null, null, deepFetch = false) } throws RuntimeException("Content is not allowed in prolog")

        val ex = assertThrows<IllegalArgumentException> {
            service.validateUrl(SourceType.RSS, "https://bad.com/html")
        }
        assertTrue(ex.message!!.contains("not appear to be a valid RSS"))
    }

    @Test
    fun `validateUrl passes for website source with content`() {
        every { websiteFetcher.fetch("https://example.com", "validation") } returns post(body = "Some article text")

        service.validateUrl(SourceType.WEBSITE, "https://example.com")
    }

    @Test
    fun `validateUrl throws for website source with empty content`() {
        every { websiteFetcher.fetch("https://empty.com", "validation") } returns null

        val ex = assertThrows<IllegalArgumentException> {
            service.validateUrl(SourceType.WEBSITE, "https://empty.com")
        }
        assertTrue(ex.message!!.contains("no extractable content"))
    }

    @Test
    fun `validateUrl skips validation for Twitter sources`() {
        service.validateUrl(SourceType.TWITTER, "https://x.com/user")
        // No exception — validation skipped
    }

    @Test
    fun `validateUrl passes category filter to RSS fetcher`() {
        every { rssFeedFetcher.fetch("https://example.com/feed", "validation", null, "tech", deepFetch = false) } returns listOf(post())

        service.validateUrl(SourceType.RSS, "https://example.com/feed", "tech")
    }
}
