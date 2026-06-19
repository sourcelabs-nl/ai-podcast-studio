package com.aisummarypodcast.publishing

import com.aisummarypodcast.store.EpisodePublication
import com.aisummarypodcast.store.PodcastPublicationTarget
import tools.jackson.databind.ObjectMapper

internal fun EpisodePublication.toResponse() = PublicationResponse(
    id = id!!,
    episodeId = episodeId,
    target = target,
    status = status.name,
    externalId = externalId,
    externalUrl = externalUrl,
    errorMessage = errorMessage,
    publishedAt = publishedAt,
    createdAt = createdAt
)

@Suppress("UNCHECKED_CAST")
internal fun PodcastPublicationTarget.toResponse(objectMapper: ObjectMapper) = PublicationTargetResponse(
    target = target,
    config = objectMapper.readValue(config, Map::class.java) as Map<String, Any>,
    enabled = enabled,
    autoPublish = autoPublish
)
