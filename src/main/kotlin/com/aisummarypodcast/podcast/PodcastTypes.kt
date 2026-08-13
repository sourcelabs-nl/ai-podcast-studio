package com.aisummarypodcast.podcast

import com.aisummarypodcast.store.Article
import com.aisummarypodcast.store.Episode
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
    val effectiveArticleCount: Long
)

/**
 * Score-stage facts for an episode's cost breakdown: how many article calls it made and, when
 * every one of them carried a provider-reported cost, their total in fractional cents. The total
 * is null as soon as one article reported nothing, so a partial sum is never presented as complete.
 */
data class ScoreStageSummary(
    val calls: Int,
    val reportedCostCents: Double?
)

data class LinkedArticlesResult(
    val articles: List<Article>,
    val topicLabels: List<String>,
    val articleTopics: Map<Long, String>
)
