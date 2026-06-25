package com.aisummarypodcast.publishing

data class PublicationResponse(
    val id: Long,
    val episodeId: Long,
    val target: String,
    val status: String,
    val externalId: String?,
    val externalUrl: String?,
    val errorMessage: String?,
    val publishedAt: String?,
    val createdAt: String
)

data class PublicationTargetRequest(
    val config: Map<String, Any>? = null,
    val enabled: Boolean = false,
    val autoPublish: Boolean = false
)

data class PublicationTargetResponse(
    val target: String,
    val config: Map<String, Any>,
    val enabled: Boolean,
    val autoPublish: Boolean
)

data class AuthorizeResponse(val authorizationUrl: String)

/** Lightweight episode reference embedded in podcast-level publication rows. */
data class PublicationEpisodeRef(
    val id: Long,
    val generatedAt: String,
    val status: String
)

/** A publication row in the podcast-level Publications tab. */
data class PodcastPublicationRow(
    val publication: PublicationResponse,
    val episode: PublicationEpisodeRef
)
