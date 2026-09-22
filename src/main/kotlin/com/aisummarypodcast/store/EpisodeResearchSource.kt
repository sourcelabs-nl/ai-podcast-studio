package com.aisummarypodcast.store

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

/**
 * One result a `webSearch` call returned while composing a focus episode, with the query that found
 * it. [ordinal] is the order the result was recorded in within the episode, so the review screen
 * lists the sources in the order the model searched for them.
 */
@Table("episode_research_sources")
data class EpisodeResearchSource(
    @Id val id: Long? = null,
    val episodeId: Long,
    val query: String,
    val title: String,
    val url: String,
    val ordinal: Int
)
