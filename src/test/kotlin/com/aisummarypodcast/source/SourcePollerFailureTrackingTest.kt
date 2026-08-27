package com.aisummarypodcast.source

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.BriefingProperties
import com.aisummarypodcast.config.EncryptionProperties
import com.aisummarypodcast.config.EpisodesProperties
import com.aisummarypodcast.config.FeedProperties
import com.aisummarypodcast.config.LlmProperties
import com.aisummarypodcast.config.SourceProperties
import com.aisummarypodcast.store.Post
import com.aisummarypodcast.store.PostRepository
import com.aisummarypodcast.store.Source
import com.aisummarypodcast.store.SourceRepository
import com.aisummarypodcast.store.SourceType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import java.time.Instant

class SourcePollerFailureTrackingTest {

    private val rssFeedFetcher = mockk<RssFeedFetcher>()
    private val websiteFetcher = mockk<WebsiteFetcher>()
    private val twitterFetcher = mockk<TwitterFetcher>()
    private val postRepository = mockk<PostRepository> {
        every { findBySourceIdAndContentHash(any(), any()) } returns null
        every { save(any()) } answers { firstArg() }
    }
    private val sourceSlot = slot<Source>()
    private val sourceRepository = mockk<SourceRepository> {
        every { save(capture(sourceSlot)) } answers { firstArg() }
    }

    private fun appProperties(maxFailures: Int = 5) = AppProperties(
        llm = LlmProperties(),
        briefing = BriefingProperties(),
        episodes = EpisodesProperties(),
        feed = FeedProperties(),
        encryption = EncryptionProperties(masterKey = "test-key"),
        source = SourceProperties(maxFailures = maxFailures)
    )

    private val source = Source(id = "s1", podcastId = "p1", type = SourceType.RSS, url = "https://example.com/feed")

    @Test
    fun `increments consecutiveFailures on transient error`() {
        every { rssFeedFetcher.fetch(any(), any(), any(), any()) } throws RuntimeException("timeout")

        val poller = SourcePoller(rssFeedFetcher, websiteFetcher, twitterFetcher, postRepository, sourceRepository, appProperties())
        poller.poll(source)

        assertEquals(1, sourceSlot.captured.consecutiveFailures)
        assertEquals("transient", sourceSlot.captured.lastFailureType)
        assertTrue(sourceSlot.captured.enabled)
    }

    @Test
    fun `increments consecutiveFailures on permanent error`() {
        every { rssFeedFetcher.fetch(any(), any(), any(), any()) } throws HttpClientErrorException(HttpStatus.NOT_FOUND)

        val poller = SourcePoller(rssFeedFetcher, websiteFetcher, twitterFetcher, postRepository, sourceRepository, appProperties())
        poller.poll(source)

        assertEquals(1, sourceSlot.captured.consecutiveFailures)
        assertEquals("permanent", sourceSlot.captured.lastFailureType)
        assertTrue(sourceSlot.captured.enabled)
    }

    // A feed served behind a decommissioned host answers 403 forever. It must record as permanent
    // so auto-disable and the host breaker can act on it, rather than backing off indefinitely.
    @Test
    fun `RSS feed returning 403 records a permanent failure`() {
        every { rssFeedFetcher.fetch(any(), any(), any(), any()) } throws HttpClientErrorException(HttpStatus.FORBIDDEN)

        val poller = SourcePoller(rssFeedFetcher, websiteFetcher, twitterFetcher, postRepository, sourceRepository, appProperties())
        poller.poll(source)

        assertEquals(1, sourceSlot.captured.consecutiveFailures)
        assertEquals("permanent", sourceSlot.captured.lastFailureType)
    }

    @Test
    fun `continues incrementing from existing failure count`() {
        val sourceWithFailures = source.copy(consecutiveFailures = 3, lastFailureType = "transient")
        every { rssFeedFetcher.fetch(any(), any(), any(), any()) } throws RuntimeException("timeout")

        val poller = SourcePoller(rssFeedFetcher, websiteFetcher, twitterFetcher, postRepository, sourceRepository, appProperties())
        poller.poll(sourceWithFailures)

        assertEquals(4, sourceSlot.captured.consecutiveFailures)
    }

    @Test
    fun `resets failure tracking on success`() {
        val sourceWithFailures = source.copy(consecutiveFailures = 3, lastFailureType = "transient")
        val post = Post(sourceId = "s1", title = "t", body = "b", url = "u", contentHash = "", createdAt = "")
        every { rssFeedFetcher.fetch(any(), any(), any(), any()) } returns listOf(post)

        val poller = SourcePoller(rssFeedFetcher, websiteFetcher, twitterFetcher, postRepository, sourceRepository, appProperties())
        poller.poll(sourceWithFailures)

        assertEquals(0, sourceSlot.captured.consecutiveFailures)
        assertNull(sourceSlot.captured.lastFailureType)
    }

    @Test
    fun `auto-disables source after max permanent failures`() {
        val sourceAtThreshold = source.copy(consecutiveFailures = 4, lastFailureType = "permanent")
        every { rssFeedFetcher.fetch(any(), any(), any(), any()) } throws HttpClientErrorException(HttpStatus.NOT_FOUND)

        val poller = SourcePoller(rssFeedFetcher, websiteFetcher, twitterFetcher, postRepository, sourceRepository, appProperties(maxFailures = 5))
        poller.poll(sourceAtThreshold)

        assertEquals(5, sourceSlot.captured.consecutiveFailures)
        assertFalse(sourceSlot.captured.enabled)
        assertNotNull(sourceSlot.captured.disabledReason)
        assertTrue(sourceSlot.captured.disabledReason!!.contains("Auto-disabled"))
    }

    @Test
    fun `does not auto-disable on transient failure even above threshold`() {
        val sourceWithManyFailures = source.copy(consecutiveFailures = 9, lastFailureType = "transient")
        every { rssFeedFetcher.fetch(any(), any(), any(), any()) } throws RuntimeException("timeout")

        val poller = SourcePoller(rssFeedFetcher, websiteFetcher, twitterFetcher, postRepository, sourceRepository, appProperties(maxFailures = 5))
        poller.poll(sourceWithManyFailures)

        assertEquals(10, sourceSlot.captured.consecutiveFailures)
        assertTrue(sourceSlot.captured.enabled)
        assertNull(sourceSlot.captured.disabledReason)
    }

    @Test
    fun `mixed failures - transient after permanent does not auto-disable`() {
        // 4 permanent failures, then a transient → should NOT auto-disable at count 5
        val sourceWith4Permanent = source.copy(consecutiveFailures = 4, lastFailureType = "permanent")
        every { rssFeedFetcher.fetch(any(), any(), any(), any()) } throws RuntimeException("timeout")

        val poller = SourcePoller(rssFeedFetcher, websiteFetcher, twitterFetcher, postRepository, sourceRepository, appProperties(maxFailures = 5))
        poller.poll(sourceWith4Permanent)

        assertEquals(5, sourceSlot.captured.consecutiveFailures)
        assertEquals("transient", sourceSlot.captured.lastFailureType)
        assertTrue(sourceSlot.captured.enabled)
        assertNull(sourceSlot.captured.disabledReason)
    }

    @Test
    fun `mixed failures - permanent after transient does auto-disable at threshold`() {
        // 5 mixed failures, then a permanent → count reaches 6, should auto-disable
        val sourceWith5Mixed = source.copy(consecutiveFailures = 5, lastFailureType = "transient")
        every { rssFeedFetcher.fetch(any(), any(), any(), any()) } throws HttpClientErrorException(HttpStatus.NOT_FOUND)

        val poller = SourcePoller(rssFeedFetcher, websiteFetcher, twitterFetcher, postRepository, sourceRepository, appProperties(maxFailures = 5))
        poller.poll(sourceWith5Mixed)

        assertEquals(6, sourceSlot.captured.consecutiveFailures)
        assertFalse(sourceSlot.captured.enabled)
        assertNotNull(sourceSlot.captured.disabledReason)
    }

    @Test
    fun `auto-disables at custom maxFailures threshold`() {
        val sourceAtThreshold = source.copy(consecutiveFailures = 2, lastFailureType = "permanent")
        every { rssFeedFetcher.fetch(any(), any(), any(), any()) } throws HttpClientErrorException(HttpStatus.NOT_FOUND)

        val poller = SourcePoller(rssFeedFetcher, websiteFetcher, twitterFetcher, postRepository, sourceRepository, appProperties(maxFailures = 3))
        poller.poll(sourceAtThreshold)

        assertEquals(3, sourceSlot.captured.consecutiveFailures)
        assertFalse(sourceSlot.captured.enabled)
        assertNotNull(sourceSlot.captured.disabledReason)
    }
}
