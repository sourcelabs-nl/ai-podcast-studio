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
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatusCode
import org.springframework.web.client.HttpClientErrorException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional

class SourcePollerTest {

    private val rssFeedFetcher = mockk<RssFeedFetcher>()
    private val websiteFetcher = mockk<WebsiteFetcher>()
    private val twitterFetcher = mockk<TwitterFetcher>()
    private val postRepository = mockk<PostRepository> {
        every { findBySourceIdAndContentHash(any(), any()) } returns null
        every { findByContentHashAndSourceIdIn(any(), any()) } returns null
        every { save(any()) } answers { firstArg() }
    }
    private val sourceRepository = mockk<SourceRepository> {
        every { save(any()) } answers { firstArg() }
    }

    private fun appProperties(maxArticleAgeDays: Int = 7) = AppProperties(
        llm = LlmProperties(),
        briefing = BriefingProperties(),
        episodes = EpisodesProperties(),
        feed = FeedProperties(),
        encryption = EncryptionProperties(masterKey = "test-key"),
        source = SourceProperties(maxArticleAgeDays = maxArticleAgeDays)
    )

    private val source = Source(id = "s1", podcastId = "p1", type = SourceType.RSS, url = "https://example.com/feed")

    private fun post(publishedAt: String? = null) = Post(
        sourceId = "s1",
        title = "Test Post",
        body = "Post body content",
        url = "https://example.com/post",
        publishedAt = publishedAt,
        contentHash = "",
        createdAt = ""
    )

    @Test
    fun `saves post within max age`() {
        val recentPost = post(publishedAt = Instant.now().minus(2, ChronoUnit.DAYS).toString())
        every { rssFeedFetcher.fetch(any(), any(), any(), any()) } returns listOf(recentPost)

        val poller = SourcePoller(rssFeedFetcher, websiteFetcher, twitterFetcher, postRepository, sourceRepository, appProperties())
        poller.poll(source)

        verify { postRepository.save(any()) }
    }

    @Test
    fun `skips post older than max age`() {
        val oldPost = post(publishedAt = Instant.now().minus(30, ChronoUnit.DAYS).toString())
        every { rssFeedFetcher.fetch(any(), any(), any(), any()) } returns listOf(oldPost)

        val poller = SourcePoller(rssFeedFetcher, websiteFetcher, twitterFetcher, postRepository, sourceRepository, appProperties())
        poller.poll(source)

        verify(exactly = 0) { postRepository.save(match { it.sourceId == "s1" && it.title == "Test Post" }) }
    }

    @Test
    fun `saves post with null publishedAt`() {
        val nullDatePost = post(publishedAt = null)
        every { rssFeedFetcher.fetch(any(), any(), any(), any()) } returns listOf(nullDatePost)

        val poller = SourcePoller(rssFeedFetcher, websiteFetcher, twitterFetcher, postRepository, sourceRepository, appProperties())
        poller.poll(source)

        verify { postRepository.save(any()) }
    }

    @Test
    fun `respects custom max article age`() {
        val post10DaysOld = post(publishedAt = Instant.now().minus(10, ChronoUnit.DAYS).toString())
        every { rssFeedFetcher.fetch(any(), any(), any(), any()) } returns listOf(post10DaysOld)

        val pollerWith7Days = SourcePoller(rssFeedFetcher, websiteFetcher, twitterFetcher, postRepository, sourceRepository, appProperties(maxArticleAgeDays = 7))
        pollerWith7Days.poll(source)
        verify(exactly = 0) { postRepository.save(match { it.title == "Test Post" }) }

        val pollerWith14Days = SourcePoller(rssFeedFetcher, websiteFetcher, twitterFetcher, postRepository, sourceRepository, appProperties(maxArticleAgeDays = 14))
        pollerWith14Days.poll(source)
        verify { postRepository.save(any()) }
    }

    @Test
    fun `per-source maxFailures override disables source at custom threshold`() {
        val failingSource = source.copy(consecutiveFailures = 2, maxFailures = 3)
        every { rssFeedFetcher.fetch(any(), any(), any(), any()) } throws HttpClientErrorException(HttpStatusCode.valueOf(404))

        val poller = SourcePoller(rssFeedFetcher, websiteFetcher, twitterFetcher, postRepository, sourceRepository, appProperties())
        poller.poll(failingSource)

        verify {
            sourceRepository.save(match {
                !it.enabled && it.consecutiveFailures == 3 && it.disabledReason != null
            })
        }
    }

    @Test
    fun `source without maxFailures override uses global default`() {
        // Global default is 15, source at 2 consecutive failures — should NOT be disabled
        val failingSource = source.copy(consecutiveFailures = 2)
        every { rssFeedFetcher.fetch(any(), any(), any(), any()) } throws HttpClientErrorException(HttpStatusCode.valueOf(404))

        val poller = SourcePoller(rssFeedFetcher, websiteFetcher, twitterFetcher, postRepository, sourceRepository, appProperties())
        poller.poll(failingSource)

        verify {
            sourceRepository.save(match {
                it.enabled && it.consecutiveFailures == 3
            })
        }
    }

    @Test
    fun `per-source maxArticleAgeDays passed to poll overrides global`() {
        // Post is 10 days old, global max is 7, but we pass 14 as override
        val post10DaysOld = post(publishedAt = Instant.now().minus(10, ChronoUnit.DAYS).toString())
        every { rssFeedFetcher.fetch(any(), any(), any(), any()) } returns listOf(post10DaysOld)

        val poller = SourcePoller(rssFeedFetcher, websiteFetcher, twitterFetcher, postRepository, sourceRepository, appProperties(maxArticleAgeDays = 7))
        poller.poll(source, maxArticleAgeDays = 14)

        verify { postRepository.save(any()) }
    }

    @Test
    fun `passes categoryFilter to rssFeedFetcher`() {
        val sourceWithFilter = source.copy(categoryFilter = "kotlin,AI")
        val recentPost = post(publishedAt = Instant.now().minus(1, ChronoUnit.DAYS).toString())
        every { rssFeedFetcher.fetch(any(), any(), any(), "kotlin,AI") } returns listOf(recentPost)

        val poller = SourcePoller(rssFeedFetcher, websiteFetcher, twitterFetcher, postRepository, sourceRepository, appProperties())
        poller.poll(sourceWithFilter)

        verify { rssFeedFetcher.fetch(sourceWithFilter.url, sourceWithFilter.id, sourceWithFilter.lastSeenId, "kotlin,AI") }
    }

    @Test
    fun `dispatches twitter source to twitterFetcher with userId`() {
        val twitterSource = Source(id = "s2", podcastId = "p1", type = SourceType.TWITTER, url = "testuser")
        val tweetPost = Post(
            sourceId = "s2",
            title = "Tweet text",
            body = "Tweet text",
            url = "https://x.com/testuser/status/123",
            publishedAt = Instant.now().toString(),
            author = "@testuser",
            contentHash = "",
            createdAt = ""
        )
        every { twitterFetcher.fetchWithUserId("testuser", "s2", null, "user1") } returns TwitterFetchResult(listOf(tweetPost), "999")
        every { twitterFetcher.buildLastSeenId(null, any(), "testuser", "user1", "999") } returns "999:123"

        val poller = SourcePoller(rssFeedFetcher, websiteFetcher, twitterFetcher, postRepository, sourceRepository, appProperties())
        poller.poll(twitterSource, userId = "user1")

        verify { postRepository.save(any()) }
        verify { twitterFetcher.fetchWithUserId("testuser", "s2", null, "user1") }
    }

    @Test
    fun `skips twitter source when no userId provided`() {
        val twitterSource = Source(id = "s2", podcastId = "p1", type = SourceType.TWITTER, url = "testuser")

        val poller = SourcePoller(rssFeedFetcher, websiteFetcher, twitterFetcher, postRepository, sourceRepository, appProperties())
        poller.poll(twitterSource, userId = null)

        verify(exactly = 0) { twitterFetcher.fetch(any(), any(), any(), any()) }
    }

    @Test
    fun `first poll skips posts published before source createdAt`() {
        val createdAt = Instant.now().toString()
        val newSource = source.copy(lastPolled = null, createdAt = createdAt)
        val oldPost = post(publishedAt = Instant.now().minus(2, ChronoUnit.DAYS).toString())
        every { rssFeedFetcher.fetch(any(), any(), any(), any()) } returns listOf(oldPost)

        val poller = SourcePoller(rssFeedFetcher, websiteFetcher, twitterFetcher, postRepository, sourceRepository, appProperties())
        poller.poll(newSource)

        verify(exactly = 0) { postRepository.save(match { it.sourceId == "s1" && it.title == "Test Post" }) }
    }

    @Test
    fun `first poll accepts posts published after source createdAt`() {
        val createdAt = Instant.now().minus(1, ChronoUnit.HOURS).toString()
        val newSource = source.copy(lastPolled = null, createdAt = createdAt)
        val recentPost = post(publishedAt = Instant.now().toString())
        every { rssFeedFetcher.fetch(any(), any(), any(), any()) } returns listOf(recentPost)

        val poller = SourcePoller(rssFeedFetcher, websiteFetcher, twitterFetcher, postRepository, sourceRepository, appProperties())
        poller.poll(newSource)

        verify { postRepository.save(any()) }
    }

    @Test
    fun `subsequent polls ignore createdAt filter`() {
        val createdAt = Instant.now().toString()
        val polledSource = source.copy(lastPolled = Instant.now().minus(1, ChronoUnit.HOURS).toString(), createdAt = createdAt)
        val olderPost = post(publishedAt = Instant.now().minus(2, ChronoUnit.DAYS).toString())
        every { rssFeedFetcher.fetch(any(), any(), any(), any()) } returns listOf(olderPost)

        val poller = SourcePoller(rssFeedFetcher, websiteFetcher, twitterFetcher, postRepository, sourceRepository, appProperties())
        poller.poll(polledSource)

        verify { postRepository.save(any()) }
    }

    @Test
    fun `cross-source duplicate is skipped within same podcast`() {
        val recentPost = post(publishedAt = Instant.now().toString())
        every { rssFeedFetcher.fetch(any(), any(), any(), any()) } returns listOf(recentPost)
        every { postRepository.findByContentHashAndSourceIdIn(any(), any()) } returns Post(
            id = 99, sourceId = "s2", title = "Existing", body = "Post body content",
            url = "https://example.com/other", contentHash = "abc", createdAt = ""
        )

        val poller = SourcePoller(rssFeedFetcher, websiteFetcher, twitterFetcher, postRepository, sourceRepository, appProperties())
        poller.poll(source.copy(lastPolled = Instant.now().toString()), siblingSourceIds = listOf("s1", "s2"))

        verify(exactly = 0) { postRepository.save(any()) }
    }

    @Test
    fun `same content hash allowed across different podcasts`() {
        val recentPost = post(publishedAt = Instant.now().toString())
        every { rssFeedFetcher.fetch(any(), any(), any(), any()) } returns listOf(recentPost)
        // No cross-source match when sibling IDs are only this source's podcast
        every { postRepository.findByContentHashAndSourceIdIn(any(), listOf("s1")) } returns null

        val poller = SourcePoller(rssFeedFetcher, websiteFetcher, twitterFetcher, postRepository, sourceRepository, appProperties())
        poller.poll(source.copy(lastPolled = Instant.now().toString()), siblingSourceIds = listOf("s1"))

        verify { postRepository.save(any()) }
    }

    @Test
    fun `first poll does not filter posts with null publishedAt`() {
        val createdAt = Instant.now().toString()
        val newSource = source.copy(lastPolled = null, createdAt = createdAt)
        val nullDatePost = post(publishedAt = null)
        every { rssFeedFetcher.fetch(any(), any(), any(), any()) } returns listOf(nullDatePost)

        val poller = SourcePoller(rssFeedFetcher, websiteFetcher, twitterFetcher, postRepository, sourceRepository, appProperties())
        poller.poll(newSource)

        verify { postRepository.save(any()) }
    }

    // --- Concurrent poll of the same source ---

    @Test
    fun `losing the optimistic lock race retries against the re-read source without counting a failure`() {
        val recentPost = post(publishedAt = Instant.now().minus(1, ChronoUnit.DAYS).toString())
        every { rssFeedFetcher.fetch(any(), any(), any(), any()) } returns listOf(recentPost)
        // A concurrent poll bumped the row first, so this poll's stale copy fails the version check.
        every { sourceRepository.save(any()) } throws
            OptimisticLockingFailureException("stale version") andThenAnswer { firstArg() }
        every { sourceRepository.findById("s1") } returns Optional.of(source.copy(version = 9))

        val poller = SourcePoller(rssFeedFetcher, websiteFetcher, twitterFetcher, postRepository, sourceRepository, appProperties())
        poller.poll(source)

        verify(exactly = 2) { sourceRepository.save(any()) }
        // A lost race is not a poll failure: the source stays healthy and enabled.
        verify(exactly = 0) { sourceRepository.save(match { it.consecutiveFailures > 0 || !it.enabled }) }
    }

    @Test
    fun `poll state is discarded when the source is deleted mid-poll`() {
        val recentPost = post(publishedAt = Instant.now().minus(1, ChronoUnit.DAYS).toString())
        every { rssFeedFetcher.fetch(any(), any(), any(), any()) } returns listOf(recentPost)
        every { sourceRepository.save(any()) } throws OptimisticLockingFailureException("row gone")
        every { sourceRepository.findById("s1") } returns Optional.empty()

        val poller = SourcePoller(rssFeedFetcher, websiteFetcher, twitterFetcher, postRepository, sourceRepository, appProperties())
        poller.poll(source)

        verify(exactly = 1) { sourceRepository.save(any()) }
    }
}
