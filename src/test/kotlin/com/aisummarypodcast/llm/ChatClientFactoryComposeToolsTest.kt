package com.aisummarypodcast.llm

import com.aisummarypodcast.research.RESEARCH_TOOL_NAME
import com.aisummarypodcast.research.ResearchService
import com.aisummarypodcast.research.ResearchTool
import com.aisummarypodcast.store.LlmCacheRepository
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.user.UserProviderConfigService
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatClientFactoryComposeToolsTest {

    private val factory = ChatClientFactory(
        providerConfigService = mockk<UserProviderConfigService>(),
        llmCacheRepository = mockk<LlmCacheRepository>(),
        episodeHistoryRepository = mockk<EpisodeHistoryRepository>(),
        researchService = mockk<ResearchService>()
    )

    private fun podcast(deepDive: Boolean) = Podcast(
        id = "p1", userId = "u1", name = "Pod", topic = "Tech", deepDiveEnabled = deepDive
    )

    @Test
    fun `deep-dive disabled registers only history tool`() {
        val budget = ToolBudget()
        val tools = factory.buildComposeTools("u1", podcast(deepDive = false), budget)

        assertEquals(1, tools.size)
        assertTrue(tools.any { it is HistoryLookupTool })
        assertFalse(tools.any { it is ResearchTool })

        // History cap exhausts after HISTORY_LOOKUP_TOOL_CAP calls.
        repeat(HISTORY_LOOKUP_TOOL_CAP) { assertTrue(budget.tryConsume(HISTORY_LOOKUP_TOOL_NAME)) }
        assertFalse(budget.tryConsume(HISTORY_LOOKUP_TOOL_NAME))

        // webSearch was never registered — tryConsume returns true (no cap).
        assertTrue(budget.tryConsume(RESEARCH_TOOL_NAME))
        assertEquals(0, budget.invocations(RESEARCH_TOOL_NAME))
    }

    @Test
    fun `deep-dive enabled registers history and research tools`() {
        val budget = ToolBudget()
        val tools = factory.buildComposeTools("u1", podcast(deepDive = true), budget)

        assertEquals(2, tools.size)
        assertTrue(tools.any { it is HistoryLookupTool })
        assertTrue(tools.any { it is ResearchTool })

        // Research cap is enforced.
        repeat(com.aisummarypodcast.research.RESEARCH_TOOL_CAP) {
            assertTrue(budget.tryConsume(RESEARCH_TOOL_NAME))
        }
        assertFalse(budget.tryConsume(RESEARCH_TOOL_NAME))
    }
}
