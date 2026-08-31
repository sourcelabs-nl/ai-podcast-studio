package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.BriefingProperties
import com.aisummarypodcast.config.ComposeProperties
import com.aisummarypodcast.config.EncryptionProperties
import com.aisummarypodcast.config.EpisodesProperties
import com.aisummarypodcast.config.FeedProperties
import com.aisummarypodcast.config.LlmProperties
import com.aisummarypodcast.store.Article
import com.aisummarypodcast.testRetryRegistry
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class TopicDedupFilterTest {

    private val appProperties = AppProperties(
        llm = LlmProperties(),
        briefing = BriefingProperties(),
        episodes = EpisodesProperties(),
        feed = FeedProperties(),
        encryption = EncryptionProperties(masterKey = "test"),
        compose = ComposeProperties(maxArticles = 40)
    )

    private val filter = TopicDedupFilter(
        mockk(), JsonMapper.builder().build(), testRetryRegistry(), appProperties
    )

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
    fun `selectArticles keeps an article selected by two clusters only once`() {
        val candidates = listOf(article(1, "Vercel fx"), article(2, "Linear agent data"))
        val clusters = listOf(
            DedupCluster(topic = "agent tooling", status = "NEW", selectedArticleIds = listOf(1, 2)),
            DedupCluster(
                topic = "coding agents",
                status = "CONTINUATION",
                previousContext = "Covered last week",
                selectedArticleIds = listOf(1)
            )
        )

        val selection = filter.selectArticles(clusters, candidates)

        assertEquals(listOf(1L, 2L), selection.articles.map { it.article.id })
        assertEquals(1, selection.duplicateSelections)
    }

    @Test
    fun `selectArticles annotates a repeated article from its first cluster`() {
        val candidates = listOf(article(1, "Vercel fx"))
        val clusters = listOf(
            DedupCluster(topic = "agent tooling", status = "NEW", selectedArticleIds = listOf(1)),
            DedupCluster(
                topic = "coding agents",
                status = "CONTINUATION",
                previousContext = "Covered last week",
                selectedArticleIds = listOf(1)
            )
        )

        val selection = filter.selectArticles(clusters, candidates)

        val only = selection.articles.single()
        assertEquals("agent tooling", only.topic)
        assertNull(only.followUpContext)
    }

    @Test
    fun `selectArticles never returns more articles than candidates`() {
        val candidates = (1L..3L).map { article(it, "Article $it") }
        // A degenerating response: 40 clusters each naming every candidate.
        val clusters = (1..40).map {
            DedupCluster(topic = "topic $it", status = "NEW", selectedArticleIds = listOf(1, 2, 3))
        }

        val selection = filter.selectArticles(clusters, candidates)

        assertEquals(3, selection.articles.size)
        assertEquals(117, selection.duplicateSelections)
    }

    @Test
    fun `selectArticles leaves a response without duplicates unchanged`() {
        val candidates = listOf(article(1, "One"), article(2, "Two"), article(3, "Three"))
        val clusters = listOf(
            DedupCluster(topic = "first", status = "NEW", selectedArticleIds = listOf(2, 1)),
            DedupCluster(
                topic = "second",
                status = "CONTINUATION",
                previousContext = "Earlier coverage",
                selectedArticleIds = listOf(3)
            )
        )

        val selection = filter.selectArticles(clusters, candidates)

        assertEquals(listOf(2L, 1L, 3L), selection.articles.map { it.article.id })
        assertEquals(listOf("first", "first", "second"), selection.articles.map { it.topic })
        assertEquals("Earlier coverage", selection.articles.last().followUpContext)
        assertEquals(0, selection.duplicateSelections)
    }

    @Test
    fun `selectArticles ignores ids that are not candidates`() {
        val candidates = listOf(article(1, "One"))
        val clusters = listOf(
            DedupCluster(topic = "hallucinated", status = "NEW", selectedArticleIds = listOf(1, 99))
        )

        val selection = filter.selectArticles(clusters, candidates)

        assertEquals(listOf(1L), selection.articles.map { it.article.id })
        assertEquals(0, selection.duplicateSelections)
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

    // --- Output token budget -------------------------------------------------------------------

    @Test
    fun `budget scales with a large candidate set`() {
        assertEquals(16470, filter.dedupOutputTokenBudget(183))
    }

    @Test
    fun `budget floors for a small candidate set`() {
        assertEquals(8000, filter.dedupOutputTokenBudget(12))
    }

    @Test
    fun `budget is capped at the ceiling for a huge candidate set`() {
        assertEquals(32000, filter.dedupOutputTokenBudget(900))
    }

    // --- Salvaging a truncated response --------------------------------------------------------

    @Test
    fun `salvages the complete clusters from a response cut off mid-element`() {
        // Episode 191's shape: the array is cut off part-way through a later element.
        val truncated = """
            {"clusters":[
              {"topic":"OpenAI ads","status":"NEW","previousContext":null,"selectedArticleIds":[1,2]},
              {"topic":"Agent memory","status":"NEW","previousContext":null,"selectedArticleIds":[3]},
              {"topic":"Gemini pri
        """.trimIndent()

        val clusters = filter.salvageClusters(truncated)

        assertEquals(2, clusters.size)
        assertEquals("OpenAI ads", clusters[0].topic)
        assertEquals(listOf(1, 2), clusters[0].selectedArticleIds)
        assertEquals("Agent memory", clusters[1].topic)
    }

    @Test
    fun `salvages a truncated response wrapped in a json fence`() {
        val truncated = """
            ```json
            {"clusters":[
              {"topic":"Only one","status":"CONTINUATION","previousContext":"Covered before","selectedArticleIds":[7]},
              {"topic":"Cut off he
        """.trimIndent()

        val clusters = filter.salvageClusters(truncated)

        assertEquals(1, clusters.size)
        assertEquals("Only one", clusters[0].topic)
        assertEquals("Covered before", clusters[0].previousContext)
    }

    @Test
    fun `salvage of a complete response matches the whole array`() {
        val complete = """{"clusters":[{"topic":"A","status":"NEW","selectedArticleIds":[1]},{"topic":"B","status":"NEW","selectedArticleIds":[2]}]}"""

        val clusters = filter.salvageClusters(complete)

        assertEquals(2, clusters.size)
        assertEquals(listOf("A", "B"), clusters.map { it.topic })
    }

    @Test
    fun `salvage returns nothing when there is no clusters array`() {
        assertTrue(filter.salvageClusters("I could not complete that request.").isEmpty())
        assertTrue(filter.salvageClusters("""{"error":"rate limited"}""").isEmpty())
    }

    @Test
    fun `salvage drops an element truncated inside its id list`() {
        val truncated = """{"clusters":[{"topic":"Kept","status":"NEW","selectedArticleIds":[1,2]},{"topic":"Lost","status":"NEW","selectedArticleIds":[3,"""

        val clusters = filter.salvageClusters(truncated)

        assertEquals(1, clusters.size)
        assertEquals("Kept", clusters[0].topic)
    }
}
