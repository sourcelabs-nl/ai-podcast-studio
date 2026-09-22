package com.aisummarypodcast.store

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

/**
 * One result a web search of the pre-compose research stage returned for an episode, with the query
 * that found it. [ordinal] is the order within the episode's latest research run, so the review
 * screen lists the sources in the order they were planned and returned.
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
