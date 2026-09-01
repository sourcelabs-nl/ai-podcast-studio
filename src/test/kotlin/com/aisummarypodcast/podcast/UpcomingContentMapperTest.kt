package com.aisummarypodcast.podcast

import com.aisummarypodcast.store.Article
import com.aisummarypodcast.store.Post
import com.aisummarypodcast.store.Source
import com.aisummarypodcast.store.SourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UpcomingContentMapperTest {

    private val source = Source(
        id = "s1", podcastId = "p1", type = SourceType.RSS,
        url = "https://rss.narro.info/feed", label = "X via Narro"
    )

    private fun article(id: Long, title: String) = Article(
        id = id, sourceId = "s1", title = title, body = "body",
        url = "https://x.com/a/status/$id", contentHash = "h$id"
    )

    @Suppress("UNCHECKED_CAST")
    private fun mapArticles(content: UpcomingContent): List<EpisodeArticleResponse> =
        content.toResponse()["articles"] as List<EpisodeArticleResponse>

    @Test
    fun `upcoming articles carry their thread size`() {
        val content = UpcomingContent(
            articles = listOf(article(1, "A thread"), article(2, "A lone post")),
            unlinkedPosts = emptyList(),
            sources = listOf(source),
            totalPostCount = 0,
            effectiveArticleCount = 2,
            postCounts = mapOf(1L to 8, 2L to 1)
        )

        val mapped = mapArticles(content)

        assertEquals(8, mapped.first { it.title == "A thread" }.postCount)
        assertEquals(1, mapped.first { it.title == "A lone post" }.postCount)
    }

    @Test
    fun `an article absent from the counts defaults to one post`() {
        val content = UpcomingContent(
            articles = listOf(article(1, "Plain RSS article")),
            unlinkedPosts = emptyList(),
            sources = listOf(source),
            totalPostCount = 0,
            effectiveArticleCount = 1,
            postCounts = emptyMap()
        )

        assertEquals(1, mapArticles(content).single().postCount)
    }

    @Test
    fun `an unlinked post mapped as an article counts as one`() {
        val content = UpcomingContent(
            articles = emptyList(),
            unlinkedPosts = listOf(
                Post(
                    id = 5, sourceId = "s1", title = "Loose post", body = "body",
                    url = "https://x.com/a/status/5", contentHash = "hp", createdAt = "2026-08-31T10:00:00Z"
                )
            ),
            sources = listOf(source),
            totalPostCount = 1,
            effectiveArticleCount = 1
        )

        // Unlinked posts are mapped into the same "articles" list as real articles.
        assertEquals(1, mapArticles(content).single().postCount)
    }
}
