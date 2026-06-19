package com.aisummarypodcast.store

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

@Table("podcast_publication_targets")
data class PodcastPublicationTarget(
    @Id val id: Long? = null,
    val podcastId: String,
    val target: String,
    val config: String = "{}",
    val enabled: Boolean = false,
    val autoPublish: Boolean = false
)
