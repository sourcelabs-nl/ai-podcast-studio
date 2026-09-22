package com.aisummarypodcast.research

import com.aisummarypodcast.store.EpisodeResearchSource
import com.aisummarypodcast.store.EpisodeResearchSourceRepository
import io.mockk.verify
import com.aisummarypodcast.llm.ToolBudget
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ResearchToolTest {

    private val researchService = mockk<ResearchService>()

    @Test
    fun `returns mapped hits and consumes budget`() {
        val budget = ToolBudget().register(RESEARCH_TOOL_NAME, RESEARCH_TOOL_CAP)
        val tool = ResearchTool(researchService, budget, "u1", "p1")
        every { researchService.search("u1", "speckit", 5) } returns TavilyResponse(
            results = listOf(TavilyResult("Speckit announces", "https://x", "snippet"))
        )

        val result = tool.webSearch("speckit")

        assertEquals(1, result.results.size)
        assertEquals("Speckit announces", result.results[0].title)
        assertEquals("snippet", result.results[0].snippet)
        assertFalse(result.budgetExhausted)
        assertEquals(1, budget.invocations(RESEARCH_TOOL_NAME))
    }

    @Test
    fun `returns budgetExhausted once cap is reached`() {
        val budget = ToolBudget().register(RESEARCH_TOOL_NAME, 1)
        val tool = ResearchTool(researchService, budget, "u1", "p1")
        every { researchService.search(any(), any(), any()) } returns TavilyResponse(
            results = listOf(TavilyResult("t", "u", "c"))
        )

        tool.webSearch("first")
        val second = tool.webSearch("second")

        assertTrue(second.budgetExhausted)
        assertTrue(second.results.isEmpty())
    }

    @Test
    fun `records every hit against the focus episode with its query`() {
        val repository = mockk<EpisodeResearchSourceRepository>()
        val saved = mutableListOf<EpisodeResearchSource>()
        every { repository.save(capture(saved)) } answers { firstArg() }
        val budget = ToolBudget().register(RESEARCH_TOOL_NAME, FOCUS_RESEARCH_TOOL_CAP)
        val tool = ResearchTool(researchService, budget, "u1", "p1", repository, recordForEpisodeId = 42L)
        every { researchService.search("u1", "Claude Opus 5.5 benchmarks", 5) } returns TavilyResponse(
            results = listOf(
                TavilyResult("A", "https://a", "a"),
                TavilyResult("B", "https://b", "b"),
                TavilyResult("C", "https://c", "c")
            )
        )

        tool.webSearch("Claude Opus 5.5 benchmarks")

        assertEquals(3, saved.size)
        assertTrue(saved.all { it.episodeId == 42L && it.query == "Claude Opus 5.5 benchmarks" })
        assertEquals(listOf("A" to "https://a", "B" to "https://b", "C" to "https://c"), saved.map { it.title to it.url })
        assertEquals(listOf(0, 1, 2), saved.map { it.ordinal })
    }

    @Test
    fun `records nothing without an episode to record against`() {
        val repository = mockk<EpisodeResearchSourceRepository>()
        val budget = ToolBudget().register(RESEARCH_TOOL_NAME, RESEARCH_TOOL_CAP)
        val tool = ResearchTool(researchService, budget, "u1", "p1", repository, recordForEpisodeId = null)
        every { researchService.search(any(), any(), any()) } returns TavilyResponse(
            results = listOf(TavilyResult("t", "u", "c"))
        )

        tool.webSearch("q")

        verify(exactly = 0) { repository.save(any()) }
    }
}
