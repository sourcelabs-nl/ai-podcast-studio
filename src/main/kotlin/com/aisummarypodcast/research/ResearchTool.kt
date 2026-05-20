package com.aisummarypodcast.research

import com.aisummarypodcast.llm.ToolBudget
import org.slf4j.LoggerFactory
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam

data class WebSearchHit(
    val title: String,
    val url: String,
    val snippet: String
)

data class WebSearchResult(
    val results: List<WebSearchHit>,
    val budgetExhausted: Boolean = false
)

const val RESEARCH_TOOL_NAME = "webSearch"

const val RESEARCH_TOOL_CAP = 3

private const val MAX_RESULTS = 5

/**
 * Spring AI tool exposed to the compose LLM when a podcast has deep-dive enabled. Scoped to
 * a single compose run via [toolBudget]; once the per-episode cap is hit, further calls
 * return an empty result list with `budgetExhausted=true` rather than throwing. Tavily
 * errors/timeouts surface as an empty list too — generation never fails because of this
 * tool.
 *
 * A fresh instance is constructed per compose call by [com.aisummarypodcast.llm.ChatClientFactory.createForCompose].
 */
class ResearchTool(
    private val researchService: ResearchService,
    private val toolBudget: ToolBudget,
    private val userId: String,
    private val podcastId: String
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Tool(
        name = RESEARCH_TOOL_NAME,
        description = "Search the web for outside context on the most newsworthy story in this episode. " +
            "Call this 1-2 times when you need background, related developments, or dissenting opinions. " +
            "Returns up to 5 ranked results, each with a title, URL, and short snippet."
    )
    fun webSearch(
        @ToolParam(description = "Short web search query (e.g. \"speckit launch reactions\"). Keep it focused.")
        query: String
    ): WebSearchResult {
        if (!toolBudget.tryConsume(RESEARCH_TOOL_NAME)) {
            log.info("[Tool] webSearch budget exhausted for podcast '{}' — query='{}'", podcastId, query)
            return WebSearchResult(results = emptyList(), budgetExhausted = true)
        }

        val response = researchService.search(userId, query, MAX_RESULTS)
        val hits = response.results.map { WebSearchHit(title = it.title, url = it.url, snippet = it.content) }

        log.info("[Tool] webSearch podcast='{}' query='{}' hits={}", podcastId, query, hits.size)
        return WebSearchResult(results = hits, budgetExhausted = false)
    }
}
