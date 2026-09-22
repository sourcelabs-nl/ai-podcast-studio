package com.aisummarypodcast.research

import com.aisummarypodcast.llm.PastEpisodeMatch
import com.aisummarypodcast.store.Podcast

/** Web search queries a regular episode of a deep-dive podcast may run before compose. */
const val RESEARCH_QUERY_CAP = 3

/** A focus episode leans on research for context its few articles lack, so it may run more. */
const val FOCUS_RESEARCH_QUERY_CAP = 5

/** Results kept per web search query. */
const val RESULTS_PER_QUERY = 5

/** Research subjects looked up in the podcast's past episodes, and matches kept per subject. */
const val HISTORY_SUBJECT_CAP = 5
const val HISTORY_MATCHES_PER_SUBJECT = 5

/** The structured answer of the research plan call. */
data class ResearchQueryPlan(val queries: List<String> = emptyList())

/** One web search result handed to the compose prompt, with the query that found it. */
data class BackgroundSource(
    val query: String,
    val title: String,
    val url: String,
    val snippet: String
)

/**
 * What the research stage found before compose: the web search results (empty when web search does
 * not apply to the run) and this podcast's past episodes matching the research subjects.
 * [researchCalls] is how many web searches ran, which the episode's research cost is charged for.
 */
data class PreComposeResearch(
    val sources: List<BackgroundSource> = emptyList(),
    val history: List<PastEpisodeMatch> = emptyList(),
    val researchCalls: Int = 0
) {
    companion object {
        val NONE = PreComposeResearch()
    }
}

/**
 * One run of the research stage. [subjects] are what to research, in priority order (the focus
 * first, then the topic clusters). [focusEpisode] raises the query cap and forces web search on.
 * [episodeId] is the episode the sources are recorded against and the plan request is attributed
 * to; null for a preview.
 */
data class ResearchRequest(
    val podcast: Podcast,
    val subjects: List<String>,
    val focusEpisode: Boolean = false,
    val episodeId: Long? = null
) {
    val webSearchEnabled: Boolean get() = focusEpisode || podcast.deepDiveEnabled

    val queryCap: Int get() = if (focusEpisode) FOCUS_RESEARCH_QUERY_CAP else RESEARCH_QUERY_CAP
}
