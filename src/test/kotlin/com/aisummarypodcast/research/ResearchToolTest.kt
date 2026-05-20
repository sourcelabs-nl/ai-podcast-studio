package com.aisummarypodcast.research

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
}
