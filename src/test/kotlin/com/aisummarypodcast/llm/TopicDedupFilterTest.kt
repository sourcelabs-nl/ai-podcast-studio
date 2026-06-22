package com.aisummarypodcast.llm

import com.aisummarypodcast.store.Article
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class TopicDedupFilterTest {

    private val filter = TopicDedupFilter(mockk(), JsonMapper.builder().build())

    private fun article(id: Long, title: String, summary: String = "Summary of $title") = Article(
        id = id,
        sourceId = "src-1",
        title = title,
        body = "Body",
        url = "https://example.com/article-$id",
        contentHash = "hash-$id",
        relevanceScore = 8,
        summary = summary
    )

    @Test
    fun `buildPrompt includes candidate articles with IDs`() {
        val candidates = listOf(
            article(1, "GPT-5 Released"),
            article(2, "Claude 4 Announced")
        )

        val prompt = filter.buildPrompt(candidates, emptyList())

        assertTrue(prompt.contains("1. [example.com] GPT-5 Released"))
        assertTrue(prompt.contains("2. [example.com] Claude 4 Announced"))
    }

    @Test
    fun `buildPrompt includes historical article titles only without summaries`() {
        val candidates = listOf(article(1, "New Article"))
        val historical = listOf(article(10, "Old Article", "Old summary"))

        val prompt = filter.buildPrompt(candidates, historical)

        assertTrue(prompt.contains("Historical articles from recent episodes"))
        assertTrue(prompt.contains("- [example.com] Old Article"))
        // Historical block is title-only to keep the dedup prompt small — summaries are omitted.
        assertTrue(!prompt.contains("Old summary"))
    }

    @Test
    fun `buildPrompt truncates oversized historical titles`() {
        val longTitle = "X".repeat(500)
        val historical = listOf(article(10, longTitle, "Old summary"))

        val prompt = filter.buildPrompt(listOf(article(1, "New Article")), historical)

        // The full 500-char title must not appear; it is truncated with an ellipsis.
        assertTrue(!prompt.contains(longTitle))
        assertTrue(prompt.contains("X".repeat(150) + "…"))
    }

    @Test
    fun `buildPrompt excludes historical section when no historical articles`() {
        val candidates = listOf(article(1, "New Article"))

        val prompt = filter.buildPrompt(candidates, emptyList())

        assertTrue(!prompt.contains("Historical articles from recent episodes"))
    }

    @Test
    fun `buildPrompt includes dedup rules`() {
        val prompt = filter.buildPrompt(listOf(article(1, "Test")), emptyList())

        assertTrue(prompt.contains("CONTINUATION"))
        assertTrue(prompt.contains("NEW"))
        assertTrue(prompt.contains("selectedArticleIds"))
        assertTrue(prompt.contains("max 3 per cluster"))
    }

    @Test
    fun `FilteredArticle has null followUpContext for NEW articles`() {
        val fa = FilteredArticle(article(1, "Test"), followUpContext = null)
        assertNull(fa.followUpContext)
    }

    @Test
    fun `FilteredArticle has followUpContext for CONTINUATION articles`() {
        val fa = FilteredArticle(article(1, "Test"), followUpContext = "Previously covered release details")
        assertEquals("Previously covered release details", fa.followUpContext)
    }
}
