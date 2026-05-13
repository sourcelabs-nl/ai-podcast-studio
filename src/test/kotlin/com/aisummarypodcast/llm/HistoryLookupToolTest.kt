package com.aisummarypodcast.llm

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HistoryLookupToolTest {

    private val repo = mockk<EpisodeHistoryRepository>()

    private fun tool(budget: ToolBudget = ToolBudget().register(HISTORY_LOOKUP_TOOL_NAME, HISTORY_LOOKUP_TOOL_CAP)) =
        HistoryLookupTool(repo, budget, podcastId = "p-1", podcastName = "Demo")

    @Test
    fun `returns matches from repository scoped to podcast`() {
        every { repo.search("p-1", "speckit", 5) } returns listOf(
            PastEpisodeMatch(7, "2026-04-01", "speckit, ai", "Recap snippet")
        )

        val result = tool().searchPastEpisodes("speckit")

        assertEquals(1, result.matches.size)
        assertFalse(result.budgetExhausted)
        assertEquals(7, result.matches[0].episodeId)
        assertEquals("Recap snippet", result.matches[0].recapSnippet)
        verify { repo.search("p-1", "speckit", 5) }
    }

    @Test
    fun `returns empty for podcast that has no prior coverage`() {
        every { repo.search("p-1", "speckit", 5) } returns emptyList()

        val result = tool().searchPastEpisodes("speckit")

        assertTrue(result.matches.isEmpty())
        assertFalse(result.budgetExhausted)
    }

    @Test
    fun `budget cap short-circuits sixth call`() {
        every { repo.search(any(), any(), any()) } returns emptyList()
        val budget = ToolBudget().register(HISTORY_LOOKUP_TOOL_NAME, HISTORY_LOOKUP_TOOL_CAP)
        val t = tool(budget)

        repeat(HISTORY_LOOKUP_TOOL_CAP) { t.searchPastEpisodes("q") }
        val exhausted = t.searchPastEpisodes("q")

        assertTrue(exhausted.budgetExhausted)
        assertTrue(exhausted.matches.isEmpty())
        verify(exactly = HISTORY_LOOKUP_TOOL_CAP) { repo.search(any(), any(), any()) }
    }

    @Test
    fun `result dto does not expose script text fields`() {
        every { repo.search("p-1", "x", 5) } returns listOf(
            PastEpisodeMatch(1, "2026-04-01", "t", "snippet")
        )

        val result = tool().searchPastEpisodes("x")

        val fieldNames = result.matches[0]::class.java.declaredFields.map { it.name }
        assertFalse(fieldNames.any { it.contains("script", ignoreCase = true) })
    }
}
