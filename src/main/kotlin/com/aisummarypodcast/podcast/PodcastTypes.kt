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

/** Score-stage facts for an episode's cost breakdown: how many article calls it made. */
data class ScoreStageSummary(
    val calls: Int
)

data class LinkedArticlesResult(
    val articles: List<Article>,
    val topicLabels: List<String>,
    val articleTopics: Map<Long, String>
)
