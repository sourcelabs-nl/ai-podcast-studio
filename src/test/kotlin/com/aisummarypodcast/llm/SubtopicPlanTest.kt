package com.aisummarypodcast.llm

import com.aisummarypodcast.store.Article
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.Subtopics
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubtopicPlanTest {

    private val basePodcast = Podcast(id = "p1", userId = "u1", name = "P", topic = "tech")

    private fun article(id: Long, subtopic: String?) =
        Article(id = id, sourceId = "s1", title = "T$id", body = "b", url = "https://x/$id", contentHash = "h$id", subtopic = subtopic)

    @Test
    fun `from returns null when no subtopics configured`() {
        val plan = SubtopicPlan.from(basePodcast, listOf(article(1, null)), targetWords = 1500, rapidFireBudgetFraction = 0.15)
        assertNull(plan)
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
        val plan = SubtopicPlan.from(podcast, articles, 1500, 0.15)!!
        assertEquals(1, plan.fullSegments.size)
        assertEquals("LLMs", plan.fullSegments[0].name)
        assertEquals(3, plan.fullSegments[0].articles.size)
        assertEquals(1, plan.rapidFire.size)
        assertEquals("Other AI", plan.rapidFire[0].name)
        assertEquals(1, plan.rapidFire[0].articles.size)
        // Full budget = 1500 * (1 - 0.15) = 1275 — single full subtopic gets it all
        assertEquals(1275, plan.fullSegments[0].wordBudget)
        assertEquals(225, plan.rapidFireWordBudget)
    }

    @Test
    fun `unclassified articles fall into Other bucket weight 1`() {
        val podcast = basePodcast.copy(
            subtopics = Subtopics(mapOf("LLMs" to 10)),
            rapidFireWeightThreshold = 3
        )
        val articles = listOf(article(1, "LLMs"), article(2, null), article(3, null))
        val plan = SubtopicPlan.from(podcast, articles, 1500, 0.15)!!
        assertEquals(listOf("LLMs"), plan.fullSegments.map { it.name })
        assertEquals(listOf("Other"), plan.rapidFire.map { it.name })
        assertEquals(2, plan.rapidFire[0].articles.size)
    }

    @Test
    fun `full-tier empty triggers null plan when all weights below threshold`() {
        val podcast = basePodcast.copy(
            subtopics = Subtopics(mapOf("LLMs" to 2, "Tools" to 1)),
            rapidFireWeightThreshold = 3
        )
        val articles = listOf(article(1, "LLMs"), article(2, "Tools"))
        val plan = SubtopicPlan.from(podcast, articles, 1500, 0.15)
        assertNull(plan)
    }

    @Test
    fun `all articles unclassified triggers null plan`() {
        val podcast = basePodcast.copy(
            subtopics = Subtopics(mapOf("LLMs" to 10)),
            rapidFireWeightThreshold = 3
        )
        val articles = listOf(article(1, null), article(2, null))
        val plan = SubtopicPlan.from(podcast, articles, 1500, 0.15)
        assertNull(plan)
    }

    @Test
    fun `proportional word budgets across two full subtopics`() {
        val podcast = basePodcast.copy(
            subtopics = Subtopics(mapOf("A" to 10, "B" to 5)),
            rapidFireWeightThreshold = 3
        )
        val articles = listOf(article(1, "A"), article(2, "B"))
        val plan = SubtopicPlan.from(podcast, articles, 1500, 0.15)!!
        val a = plan.fullSegments.first { it.name == "A" }.wordBudget
        val b = plan.fullSegments.first { it.name == "B" }.wordBudget
        // Budgets are integer-truncated: 1500 * (1-0.15) = 1275; A=850, B=425
        assertEquals(850, a)
        assertEquals(425, b)
        // No rapid-fire content → rapidFireWordBudget collapses to 0
        assertEquals(0, plan.rapidFireWordBudget)
        assertFalse(plan.hasRapidFire)
    }

    @Test
    fun `articleSubtopics maps each article id to its bucket`() {
        val podcast = basePodcast.copy(
            subtopics = Subtopics(mapOf("LLMs" to 10, "Tools" to 1)),
            rapidFireWeightThreshold = 3
        )
        val articles = listOf(article(1, "LLMs"), article(2, "Tools"), article(3, null))
        val plan = SubtopicPlan.from(podcast, articles, 1500, 0.15)!!
        assertEquals("LLMs", plan.articleSubtopics[1])
        assertEquals("Tools", plan.articleSubtopics[2])
        assertEquals("Other", plan.articleSubtopics[3])
    }

    @Test
    fun `subtopic match is case-insensitive against configured names`() {
        val podcast = basePodcast.copy(
            subtopics = Subtopics(mapOf("LLM releases" to 10)),
            rapidFireWeightThreshold = 3
        )
        val articles = listOf(article(1, "llm releases"))
        val plan = SubtopicPlan.from(podcast, articles, 1500, 0.15)!!
        assertEquals("LLM releases", plan.fullSegments[0].name)
        assertEquals(1, plan.fullSegments[0].articles.size)
    }

    @Test
    fun `buildSubtopicPlanBlock briefing includes And in brief label`() {
        val plan = SubtopicPlan(
            fullSegments = listOf(SubtopicBucket("A", 10, listOf(article(1, "A")), 850)),
            rapidFire = listOf(SubtopicBucket("Other", 1, listOf(article(2, null)), 0)),
            rapidFireWordBudget = 225,
            targetWords = 1500,
            rapidFireWeightThreshold = 3
        )
        val block = buildSubtopicPlanBlock(plan, RapidFireStyle.BRIEFING)
        assertTrue(block.contains("And in brief"))
        assertTrue(block.contains("\"A\""))
        assertTrue(block.contains("\"Other\""))
        assertTrue(block.contains("850"))
        assertTrue(block.contains("225"))
    }

    @Test
    fun `buildSubtopicPlanBlock no rapid-fire when only full segments`() {
        val plan = SubtopicPlan(
            fullSegments = listOf(SubtopicBucket("A", 10, listOf(article(1, "A")), 1500)),
            rapidFire = emptyList(),
            rapidFireWordBudget = 0,
            targetWords = 1500,
            rapidFireWeightThreshold = 3
        )
        val block = buildSubtopicPlanBlock(plan, RapidFireStyle.BRIEFING)
        assertFalse(block.contains("And in brief"))
        assertFalse(block.contains("Rapid-fire tier"))
    }
}
