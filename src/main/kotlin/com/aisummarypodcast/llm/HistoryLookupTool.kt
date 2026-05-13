package com.aisummarypodcast.llm

import org.slf4j.LoggerFactory
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam

data class PastEpisodeMatchDto(
    val episodeId: Long,
    val generatedAt: String,
    val topics: String,
    val recapSnippet: String
)

data class SearchPastEpisodesResult(
    val matches: List<PastEpisodeMatchDto>,
    val budgetExhausted: Boolean = false
)

const val HISTORY_LOOKUP_TOOL_NAME = "searchPastEpisodes"

const val HISTORY_LOOKUP_TOOL_CAP = 5

/**
 * Spring AI tool exposed to the compose LLM. Scoped to a single podcast (the podcast being
 * composed). The [ToolBudget] caps how many times the LLM can call this tool per episode
 * generation; once the cap is hit, further calls return an empty match list with
 * `budgetExhausted=true` rather than throwing.
 *
 * A fresh instance is constructed per compose call by [ChatClientFactory.createForCompose].
 */
class HistoryLookupTool(
    private val episodeHistoryRepository: EpisodeHistoryRepository,
    private val toolBudget: ToolBudget,
    private val podcastId: String,
    private val podcastName: String
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Tool(
        name = HISTORY_LOOKUP_TOOL_NAME,
        description = "Search this podcast's prior episodes (recaps, scripts, topic labels) for previous coverage of a subject. " +
            "Call this with one or two keywords BEFORE treating any topic as new. Returns up to 5 ranked past-episode matches, " +
            "each with the episode's date, topic labels, and a short recap snippet."
    )
    fun searchPastEpisodes(
        @ToolParam(description = "Keyword(s) describing the topic to check (e.g. \"speckit\", \"OpenAI o3\"). Keep it short.")
        query: String
    ): SearchPastEpisodesResult {
        if (!toolBudget.tryConsume(HISTORY_LOOKUP_TOOL_NAME)) {
            log.info(
                "[Tool] searchPastEpisodes budget exhausted for podcast '{}' ({}) — query={}",
                podcastName, podcastId, query
            )
            return SearchPastEpisodesResult(matches = emptyList(), budgetExhausted = true)
        }

        val matches = episodeHistoryRepository.search(podcastId, query, limit = 5)
            .map {
                PastEpisodeMatchDto(
                    episodeId = it.episodeId,
                    generatedAt = it.generatedAt,
                    topics = it.topics,
                    recapSnippet = it.recapSnippet
                )
            }

        log.info(
            "[Tool] searchPastEpisodes podcast='{}' ({}) query='{}' matches={}",
            podcastName, podcastId, query, matches.size
        )
        return SearchPastEpisodesResult(matches = matches, budgetExhausted = false)
    }
}
