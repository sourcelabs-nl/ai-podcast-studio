package com.aisummarypodcast.llm

import com.aisummarypodcast.store.Article
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.Subtopics
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubtopicPlanTest {

    private val basePodcast = Podcast(id = "p1", userId = "u1", name = "P", topic = "tech")

    private fun article(id: Long, subtopic: String?, relevanceScore: Int? = null) =
        Article(
            id = id, sourceId = "s1", title = "T$id", body = "b", url = "https://x/$id",
            contentHash = "h$id", subtopic = subtopic, relevanceScore = relevanceScore
        )

    private fun plan(podcast: Podcast, articles: List<Article>, maxItems: Int = 50) =
        SubtopicPlan.from(podcast, articles, targetWords = 1500, rapidFireBudgetFraction = 0.15, rapidFireMaxItems = maxItems)

    @Test
    fun `from returns null when no subtopics configured`() {
        assertNull(plan(basePodcast, listOf(article(1, null))))
    }

    @Test
    fun `mixed full and rapid-fire tiers are produced`() {
        val podcast = basePodcast.copy(
            subtopics = Subtopics(mapOf("LLMs" to 10, "Other AI" to 1)),
            rapidFireWeightThreshold = 3
        )
        val articles = listOf(
            article(1, "LLMs"), article(2, "LLMs"), article(3, "LLMs"),
            article(4, "Other AI")
        )
        val p = plan(podcast, articles)!!
        assertEquals(1, p.fullSegments.size)
        assertEquals("LLMs", p.fullSegments[0].name)
        assertEquals(3, p.fullSegments[0].articles.size)
        assertEquals(1, p.rapidFire.size)
        assertEquals("Other AI", p.rapidFire[0].name)
        assertEquals(1, p.rapidFire[0].articles.size)
        assertEquals(1275, p.fullSegments[0].wordBudget)
        assertEquals(225, p.rapidFireWordBudget)
    }

    @Test
    fun `unclassified articles fall into Other bucket weight 1`() {
        val podcast = basePodcast.copy(
            subtopics = Subtopics(mapOf("LLMs" to 10)),
            rapidFireWeightThreshold = 3
        )
        val articles = listOf(article(1, "LLMs"), article(2, null), article(3, null))
        val p = plan(podcast, articles)!!
        assertEquals(listOf("LLMs"), p.fullSegments.map { it.name })
        assertEquals(listOf("Other"), p.rapidFire.map { it.name })
        assertEquals(2, p.rapidFire[0].articles.size)
    }

    @Test
    fun `full-tier empty triggers null plan when all weights below threshold`() {
        val podcast = basePodcast.copy(
            subtopics = Subtopics(mapOf("LLMs" to 2, "Tools" to 1)),
            rapidFireWeightThreshold = 3
        )
        assertNull(plan(podcast, listOf(article(1, "LLMs"), article(2, "Tools"))))
    }

    @Test
    fun `all articles unclassified triggers null plan`() {
        val podcast = basePodcast.copy(
            subtopics = Subtopics(mapOf("LLMs" to 10)),
            rapidFireWeightThreshold = 3
        )
        assertNull(plan(podcast, listOf(article(1, null), article(2, null))))
    }

    @Test
    fun `proportional word budgets across two full subtopics`() {
        val podcast = basePodcast.copy(
            subtopics = Subtopics(mapOf("A" to 10, "B" to 5)),
            rapidFireWeightThreshold = 3
        )
        val p = plan(podcast, listOf(article(1, "A"), article(2, "B")))!!
        assertEquals(850, p.fullSegments.first { it.name == "A" }.wordBudget)
        assertEquals(425, p.fullSegments.first { it.name == "B" }.wordBudget)
        assertEquals(0, p.rapidFireWordBudget)
        assertFalse(p.hasRapidFire)
    }

    @Test
    fun `articleSubtopics maps each article id to its bucket`() {
        val podcast = basePodcast.copy(
            subtopics = Subtopics(mapOf("LLMs" to 10, "Tools" to 1)),
            rapidFireWeightThreshold = 3
        )
        val p = plan(podcast, listOf(article(1, "LLMs"), article(2, "Tools"), article(3, null)))!!
        assertEquals("LLMs", p.articleSubtopics[1])
        assertEquals("Tools", p.articleSubtopics[2])
        assertEquals("Other", p.articleSubtopics[3])
    }

    @Test
    fun `subtopic match is case-insensitive against configured names`() {
        val podcast = basePodcast.copy(
            subtopics = Subtopics(mapOf("LLM releases" to 10)),
            rapidFireWeightThreshold = 3
        )
        val p = plan(podcast, listOf(article(1, "llm releases")))!!
        assertEquals("LLM releases", p.fullSegments[0].name)
        assertEquals(1, p.fullSegments[0].articles.size)
    }

    @Test
    fun `rapid-fire cap drops lowest-priority articles`() {
        val podcast = basePodcast.copy(
            subtopics = Subtopics(mapOf("LLMs" to 10, "Benchmarks" to 2, "Industry" to 1)),
            rapidFireWeightThreshold = 3
        )
        val articles = listOf(
            article(1, "LLMs"),
            article(2, "Benchmarks", relevanceScore = 7),
            article(3, "Benchmarks", relevanceScore = 5),
            article(4, "Industry", relevanceScore = 9),
            article(5, "Industry", relevanceScore = 8)
        )
        val p = plan(podcast, articles, maxItems = 2)!!
        // Cap=2, ranked by (weight desc, score desc): Benchmarks#2 (w2,s7), Benchmarks#3 (w2,s5)
        // Industry articles (w1) are dropped even though they have higher scores.
        assertEquals(2, p.rapidFireOrder.size)
        assertEquals(listOf(2L, 3L), p.rapidFireOrder.map { it.article.id })
        assertEquals(listOf("Benchmarks"), p.rapidFire.map { it.name })
        // Dropped articles must not appear in articleSubtopics.
        assertNull(p.articleSubtopics[4])
        assertNull(p.articleSubtopics[5])
    }

    @Test
    fun `rapid-fire ranking prefers higher relevance score within same bucket weight`() {
        val podcast = basePodcast.copy(
            subtopics = Subtopics(mapOf("LLMs" to 10, "Industry" to 1)),
            rapidFireWeightThreshold = 3
        )
        val articles = listOf(
            article(1, "LLMs"),
            article(2, "Industry", relevanceScore = 3),
            article(3, "Industry", relevanceScore = 9),
            article(4, "Industry", relevanceScore = null)
        )
        val p = plan(podcast, articles, maxItems = 10)!!
        assertEquals(listOf(3L, 2L, 4L), p.rapidFireOrder.map { it.article.id })
    }

    @Test
    fun `rapid-fire cap of zero drops the whole tier`() {
        val podcast = basePodcast.copy(
            subtopics = Subtopics(mapOf("LLMs" to 10, "Industry" to 1)),
            rapidFireWeightThreshold = 3
        )
        val p = plan(podcast, listOf(article(1, "LLMs"), article(2, "Industry")), maxItems = 0)!!
        assertFalse(p.hasRapidFire)
        assertEquals(0, p.rapidFireWordBudget)
        assertEquals(0, p.rapidFireOrder.size)
    }

    @Test
    fun `buildSubtopicPlanBlock interview lists items in priority order with per-item budget`() {
        val podcast = basePodcast.copy(
            subtopics = Subtopics(mapOf("LLMs" to 10, "Benchmarks" to 2)),
            rapidFireWeightThreshold = 3
        )
        val articles = listOf(
            article(1, "LLMs"),
            article(2, "Benchmarks", relevanceScore = 5).copy(title = "Erdos breakthrough"),
            article(3, "Benchmarks", relevanceScore = 3).copy(title = "Routine survey")
        )
        val p = plan(podcast, articles, maxItems = 2)!!
        val block = buildSubtopicPlanBlock(p, RapidFireStyle.INTERVIEW)
        assertTrue(block.contains("Lightning round"))
        assertTrue(block.contains("1. [Benchmarks] \"Erdos breakthrough\""))
        assertTrue(block.contains("2. [Benchmarks] \"Routine survey\""))
        // Budget = 1500 * 0.15 = 225 words, 2 items => 112 per item
        assertTrue(block.contains("~112 words per item"), "Expected per-item budget, got:\n$block")
        assertTrue(block.contains("exactly 2 items"))
    }

    @Test
    fun `buildSubtopicPlanBlock briefing includes And in brief label`() {
        val podcast = basePodcast.copy(
            subtopics = Subtopics(mapOf("LLMs" to 10, "Other AI" to 1)),
            rapidFireWeightThreshold = 3
        )
        val p = plan(podcast, listOf(article(1, "LLMs"), article(2, "Other AI")))!!
        val block = buildSubtopicPlanBlock(p, RapidFireStyle.BRIEFING)
        assertTrue(block.contains("And in brief"))
        assertTrue(block.contains("\"LLMs\""))
    }

    @Test
    fun `buildSubtopicPlanBlock no rapid-fire when only full segments`() {
        val podcast = basePodcast.copy(
            subtopics = Subtopics(mapOf("A" to 10)),
            rapidFireWeightThreshold = 3
        )
        val p = plan(podcast, listOf(article(1, "A")))!!
        val block = buildSubtopicPlanBlock(p, RapidFireStyle.BRIEFING)
        assertFalse(block.contains("And in brief"))
        assertFalse(block.contains("Rapid-fire tier"))
    }
}
