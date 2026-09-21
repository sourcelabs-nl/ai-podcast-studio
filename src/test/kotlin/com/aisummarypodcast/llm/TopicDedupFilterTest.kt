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
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.metadata.ChatGenerationMetadata
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
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
        mockk(), JsonMapper.builder().build(), testRetryRegistry(), appProperties, mockk()
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

        val prompt = filter.buildPrompt(candidates, EpisodeHistory.EMPTY)

        assertTrue(prompt.contains("1. [example.com] GPT-5 Released"))
        assertTrue(prompt.contains("2. [example.com] Claude 4 Announced"))
    }

    @Test
    fun `buildPrompt includes historical article titles only without summaries`() {
        val candidates = listOf(article(1, "New Article"))
        val historical = listOf(article(10, "Old Article", "Old summary"))

        val prompt = filter.buildPrompt(candidates, EpisodeHistory(historical, emptyList()))

        assertTrue(prompt.contains("Historical articles from recent episodes"))
        assertTrue(prompt.contains("- [example.com] Old Article"))
        // Historical block is title-only to keep the dedup prompt small — summaries are omitted.
        assertTrue(!prompt.contains("Old summary"))
    }

    @Test
    fun `buildPrompt truncates oversized historical titles`() {
        val longTitle = "X".repeat(500)
        val historical = listOf(article(10, longTitle, "Old summary"))

        val prompt = filter.buildPrompt(listOf(article(1, "New Article")), EpisodeHistory(historical, emptyList()))

        // The full 500-char title must not appear; it is truncated with an ellipsis.
        assertTrue(!prompt.contains(longTitle))
        assertTrue(prompt.contains("X".repeat(150) + "…"))
    }

    @Test
    fun `buildPrompt excludes historical section when no historical articles`() {
        val candidates = listOf(article(1, "New Article"))

        val prompt = filter.buildPrompt(candidates, EpisodeHistory.EMPTY)

        assertTrue(!prompt.contains("Historical articles from recent episodes"))
    }

    @Test
    fun `buildPrompt includes dedup rules`() {
        val prompt = filter.buildPrompt(listOf(article(1, "Test")), EpisodeHistory.EMPTY)

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

    // --- Finish reason -------------------------------------------------------------------------

    private fun responseFinishing(finishReason: String?) = ChatResponse(
        listOf(
            Generation(
                AssistantMessage("{}"),
                ChatGenerationMetadata.builder().finishReason(finishReason).build()
            )
        )
    )

    @Test
    fun `a response truncated at the token cap is reported`() {
        // The signature that identified episode 200's cause: the cap was reached, not the network.
        assertEquals("LENGTH", filter.abnormalFinishReason(responseFinishing("LENGTH")))
        assertEquals("CONTENT_FILTER", filter.abnormalFinishReason(responseFinishing("CONTENT_FILTER")))
    }

    @Test
    fun `a normal stop is not reported`() {
        assertNull(filter.abnormalFinishReason(responseFinishing("STOP")))
        assertNull(filter.abnormalFinishReason(responseFinishing("stop")))
    }

    @Test
    fun `a missing finish reason is not reported`() {
        // A provider that reports nothing tells us nothing; warning here would bury the LENGTH case.
        assertNull(filter.abnormalFinishReason(responseFinishing(null)))
        assertNull(filter.abnormalFinishReason(responseFinishing(" ")))
        assertNull(filter.abnormalFinishReason(null))
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
    fun `salvages the clusters from a bare array cut off mid-element`() {
        val truncated = """
            [
              {"topic":"Kept","status":"NEW","selectedArticleIds":[1,2]},
              {"topic":"Cut off he
        """.trimIndent()

        val clusters = filter.salvageClusters(truncated)

        assertEquals(1, clusters.size)
        assertEquals("Kept", clusters[0].topic)
    }

    @Test
    fun `salvage drops an element truncated inside its id list`() {
        val truncated = """{"clusters":[{"topic":"Kept","status":"NEW","selectedArticleIds":[1,2]},{"topic":"Lost","status":"NEW","selectedArticleIds":[3,"""

        val clusters = filter.salvageClusters(truncated)

        assertEquals(1, clusters.size)
        assertEquals("Kept", clusters[0].topic)
    }

    // --- Parsing an off-schema response ---------------------------------------------------------

    @Test
    fun `parses a response wrapped in prose and a json fence`() {
        // Episode 202's shape: complete, valid JSON behind a prose lead-in and a code fence.
        val raw = """
             **Output:**

            ```json
            {"clusters":[{"topic":"A","status":"NEW","selectedArticleIds":[1]}]}
            ```
        """.trimIndent()

        val result = filter.parseOrSalvage(raw, listOf(article(1, "One")))

        assertEquals(listOf("A"), result.clusters.map { it.topic })
    }

    @Test
    fun `parses a bare cluster array instead of the asked-for object`() {
        val raw = """[{"topic":"A","status":"NEW","selectedArticleIds":[1]},{"topic":"B","status":"NEW","selectedArticleIds":[2]}]"""

        val result = filter.parseOrSalvage(raw, listOf(article(1, "One"), article(2, "Two")))

        assertEquals(listOf("A", "B"), result.clusters.map { it.topic })
    }

    @Test
    fun `parses the plain object the prompt asks for`() {
        val raw = """{"clusters":[{"topic":"A","status":"NEW","selectedArticleIds":[1]}]}"""

        val result = filter.parseOrSalvage(raw, listOf(article(1, "One")))

        assertEquals(listOf("A"), result.clusters.map { it.topic })
    }

    @Test
    fun `a response holding no JSON at all fails rather than composing on nothing`() {
        assertThrows(IllegalStateException::class.java) {
            filter.parseOrSalvage("I could not complete that request.", listOf(article(1, "One")))
        }
    }

    @Test
    fun `parses a response with chatter after the JSON`() {
        // A stray bracket in the sign-off must not be mistaken for the end of the payload.
        val raw = """
            {"clusters":[{"topic":"A","status":"NEW","selectedArticleIds":[1]}]}

            Let me know if you need anything else]
        """.trimIndent()

        val result = filter.parseOrSalvage(raw, listOf(article(1, "One")))

        assertEquals(listOf("A"), result.clusters.map { it.topic })
    }

    // --- Retry prompts --------------------------------------------------------------------------

    @Test
    fun `the first attempt sends the prompt unchanged`() {
        assertEquals("PROMPT", filter.promptForAttempt("PROMPT", 1))
    }

    @Test
    fun `a retry escalates the prompt so it is never replayed from cache`() {
        // CachingChatModel keys on prompt text: a byte-identical retry would replay the cached
        // unparseable answer instead of calling the model.
        val second = filter.promptForAttempt("PROMPT", 2)
        val third = filter.promptForAttempt("PROMPT", 3)

        assertTrue(second.startsWith("PROMPT"))
        assertTrue(second.contains("Retry 2"))
        assertTrue(second.contains("clusters"))
        assertTrue(third != second)
    }

    // --- Rejecting a degenerate response -------------------------------------------------------

    private fun cluster(topic: String, status: String, ids: List<Int>) =
        DedupCluster(topic = topic, status = status, selectedArticleIds = ids)

    @Test
    fun `rejects a response whose NEW clusters mostly select nothing`() {
        // Episode 204's shape: 34 NEW clusters, 33 of them naming a topic and selecting no article.
        val clusters = listOf(cluster("Mantis", "NEW", listOf(248317))) +
            (1..33).map { cluster("Topic $it", "NEW", emptyList()) }

        val error = assertThrows(IllegalStateException::class.java) {
            filter.requireUsableClusters(clusters)
        }

        assertTrue(error.message!!.contains("33 of 34"), error.message)
    }

    @Test
    fun `drops a minority of empty NEW clusters and keeps the rest`() {
        val clusters = (1..28).map { cluster("Kept $it", "NEW", listOf(it)) } +
            listOf(cluster("Dropped A", "NEW", emptyList()), cluster("Dropped B", "NEW", emptyList()))

        val usable = filter.requireUsableClusters(clusters)

        assertEquals(28, usable.size)
        assertTrue(usable.none { it.topic.startsWith("Dropped") })
    }

    @Test
    fun `accepts continuations that select nothing`() {
        val clusters = (1..5).map { cluster("Covered $it", "CONTINUATION", emptyList()) }

        val usable = filter.requireUsableClusters(clusters)

        assertEquals(5, usable.size)
    }

    @Test
    fun `a degenerate response that parses cleanly is still rejected`() {
        val raw = """{"clusters":[{"topic":"A","status":"NEW","selectedArticleIds":[1]},""" +
            """{"topic":"B","status":"NEW","selectedArticleIds":[]},""" +
            """{"topic":"C","status":"NEW","selectedArticleIds":[]}]}"""

        assertThrows(IllegalStateException::class.java) {
            filter.parseOrSalvage(raw, listOf(article(1, "A")))
        }
    }

    @Test
    fun `a healthy response that parses cleanly is returned unchanged`() {
        val raw = """{"clusters":[{"topic":"A","status":"NEW","selectedArticleIds":[1]},""" +
            """{"topic":"B","status":"NEW","selectedArticleIds":[2]}]}"""

        val result = filter.parseOrSalvage(raw, listOf(article(1, "A"), article(2, "B")))

        assertEquals(2, result.clusters.size)
    }

    // --- Covered topics (episode 222's repeated DeepSeek release) ---

    @Test
    fun `buildPrompt includes the topics recent episodes covered`() {
        val history = EpisodeHistory(
            articles = emptyList(),
            coveredTopics = listOf("DeepSeek v4.1 Flash vs GLM 5.3 Flash comparison", "Gemini 3.8 Live")
        )

        val prompt = filter.buildPrompt(listOf(article(1, "DeepSeek KV cache report")), history)

        assertTrue(prompt.contains("Topics already covered in recent episodes"))
        assertTrue(prompt.contains("- DeepSeek v4.1 Flash vs GLM 5.3 Flash comparison"))
        assertTrue(prompt.contains("- Gemini 3.8 Live"))
    }

    @Test
    fun `buildPrompt excludes the covered-topics section when there are none`() {
        val prompt = filter.buildPrompt(listOf(article(1, "New Article")), EpisodeHistory.EMPTY)

        assertTrue(!prompt.contains("Topics already covered in recent episodes"))
    }

    @Test
    fun `buildPrompt tells the model a fresh analysis of a covered release is a continuation`() {
        val prompt = filter.buildPrompt(listOf(article(1, "Test")), EpisodeHistory.EMPTY)

        assertTrue(prompt.contains("is a CONTINUATION, not a NEW release"))
    }
}
