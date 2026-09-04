package com.aisummarypodcast.store

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

@Table("episode_articles")
data class EpisodeArticle(
    @Id val id: Long? = null,
    val episodeId: Long,
    val articleId: Long,
    val topic: String? = null,
    val topicOrder: Int? = null,
    /** The dedup cluster's previous-coverage sentence, or null for a NEW topic. */
    val followUpContext: String? = null
)
