package com.aisummarypodcast.podcast

import com.aisummarypodcast.store.Article
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodeMatchDetails
import com.aisummarypodcast.store.Post
import com.aisummarypodcast.store.Source

enum class ResumePoint {
    FULL_PIPELINE,
    COMPOSE,
    POST_COMPOSE
}

data class GenerateBriefingResult(
    val episode: Episode?,
    val failed: Boolean = false,
    val errorMessage: String? = null
)

data class UpcomingContent(
    val articles: List<Article>,
    val unlinkedPosts: List<Post>,
    val sources: List<Source>,
    val totalPostCount: Long,
    val effectiveArticleCount: Long,
    /** Posts each article was aggregated from, keyed by article id. Absent means one. */
    val postCounts: Map<Long, Int> = emptyMap()
)

/** Score-stage facts for an episode's cost breakdown: how many article calls it made. */
data class ScoreStageSummary(
    val calls: Int
)

data class LinkedArticlesResult(
    val articles: List<Article>,
    val topicLabels: List<String>,
    val articleTopics: Map<Long, String>
)

/** An episode returned by a search, paired with the topics and titles that matched the query. */
data class EpisodeSearchHit(
    val episode: Episode,
    val matches: EpisodeMatchDetails,
    /** The spoken text around the keyword, when the script (or recap, or notes) mentions it. */
    val scriptContext: String? = null
)
