package com.aisummarypodcast.publishing

import com.aisummarypodcast.podcast.StaticFeedExporter
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodePublication
import com.aisummarypodcast.store.EpisodePublicationRepository
import com.aisummarypodcast.store.EpisodeRepository
import com.aisummarypodcast.store.EpisodeStatus
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.PublicationStatus
import com.aisummarypodcast.podcast.PodcastEvent
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class PublishingService(
    private val publisherRegistry: PublisherRegistry,
    private val publicationRepository: EpisodePublicationRepository,
    private val episodeRepository: EpisodeRepository,
    private val soundCloudPublisher: SoundCloudPublisher,
    private val targetService: PodcastPublicationTargetService,
    private val staticFeedExporter: StaticFeedExporter,
    private val eventPublisher: ApplicationEventPublisher
) {

    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun publish(episode: Episode, podcast: Podcast, userId: String, target: String): EpisodePublication {
        val publisher = publisherRegistry.getPublisher(target)
            ?: throw IllegalArgumentException("Unsupported publish target: $target")

        val publicationTarget = targetService.get(podcast.id, target)
        if (publicationTarget == null || !publicationTarget.enabled) {
            throw IllegalStateException("Publication target '$target' is not configured or enabled for this podcast")
        }

        if (episode.status != EpisodeStatus.GENERATED) {
            throw IllegalStateException("Episode must be in GENERATED status to publish (current: ${episode.status})")
        }

        if (podcast.requirePublishApproval && !episode.publishApproved) {
            throw IllegalStateException("Episode must be approved for publication before it can be published")
        }

        if (episode.audioFilePath == null) {
            throw IllegalStateException("Episode has no audio file")
        }

        val existing = publicationRepository.findByEpisodeIdAndTarget(episode.id!!, target)

        if (existing?.status == PublicationStatus.PUBLISHED && existing.externalId != null) {
            return updateExisting(publisher, episode, podcast, userId, existing)
        }

        // Replace any existing publication from another episode with the same date (regenerated episodes)
        val episodeDate = episode.generatedAt.substring(0, 10) // YYYY-MM-DD
        val previousPublications = publicationRepository.findPublishedByPodcastIdAndTarget(podcast.id, target)
        for (prev in previousPublications) {
            if (prev.episodeId == episode.id) continue
            val prevEpisode = episodeRepository.findByIdOrNull(prev.episodeId) ?: continue
            val prevDate = prevEpisode.generatedAt.substring(0, 10)
            if (prevDate != episodeDate) continue
            try {
                if (prev.externalId != null) {
                    publisher.unpublish(userId, prev.externalId)
                }
                log.info("Replaced same-day publication (episode {}, externalId={}) on {}", prev.episodeId, prev.externalId, target)
            } catch (e: Exception) {
                log.warn("Failed to unpublish previous episode {} from {}: {}", prev.episodeId, target, e.message)
            }
            publicationRepository.delete(prev)
        }

        val now = Instant.now().toString()
        val publication = publicationRepository.save(
            EpisodePublication(
                id = existing?.id,
                episodeId = episode.id,
                target = target,
                status = PublicationStatus.PENDING,
                createdAt = existing?.createdAt ?: now
            )
        )

        return try {
            log.info("Publishing episode {} to {}", episode.id, target)
            val result = publisher.publish(episode, podcast, userId)

            val published = publicationRepository.save(
                publication.copy(
                    status = PublicationStatus.PUBLISHED,
                    externalId = result.externalId,
                    externalUrl = result.externalUrl,
                    publishedAt = Instant.now().toString()
                )
            )
            log.info("Episode {} published to {} (externalId={})", episode.id, target, result.externalId)
            publisher.postPublish(podcast, userId)
            if (target == SoundCloudPublisher.TARGET_NAME) {
                try {
                    rebuildSoundCloudPlaylist(podcast, userId)
                } catch (e: Exception) {
                    log.warn("Failed to rebuild SoundCloud playlist after publish: {}", e.message)
                }
            }
            staticFeedExporter.export(podcast)
            eventPublisher.publishEvent(
                PodcastEvent(this, podcast.id, "publication", episode.id!!, "episode.published",
                    mapOf("episodeNumber" to episode.id, "target" to target))
            )
            published
        } catch (e: Exception) {
            log.error("Failed to publish episode {} to {}: {}", episode.id, target, e.message, e)
            publicationRepository.save(
                publication.copy(
                    status = PublicationStatus.FAILED,
                    errorMessage = e.message
                )
            )
            eventPublisher.publishEvent(
                PodcastEvent(this, podcast.id, "publication", episode.id!!, "episode.publish.failed",
                    mapOf("episodeNumber" to episode.id, "target" to target, "error" to (e.message ?: "Unknown error")))
            )
            throw e
        }
    }

    private fun updateExisting(
        publisher: EpisodePublisher,
        episode: Episode,
        podcast: Podcast,
        userId: String,
        existing: EpisodePublication
    ): EpisodePublication {
        return try {
            log.info("Updating episode {} on {} (externalId={})", episode.id, existing.target, existing.externalId)
            val result = publisher.update(episode, podcast, userId, existing.externalId!!)
            val updated = publicationRepository.save(
                existing.copy(
                    externalUrl = result.externalUrl,
                    publishedAt = Instant.now().toString(),
                    errorMessage = null
                )
            )
            log.info("Episode {} updated on {} (externalId={})", episode.id, existing.target, existing.externalId)
            publisher.postPublish(podcast, userId)
            if (existing.target == SoundCloudPublisher.TARGET_NAME) {
                try {
                    rebuildSoundCloudPlaylist(podcast, userId)
                } catch (e: Exception) {
                    log.warn("Failed to rebuild SoundCloud playlist after update: {}", e.message)
                }
            }
            staticFeedExporter.export(podcast)
            updated
        } catch (e: UnsupportedOperationException) {
            log.warn("Publisher {} does not support updates: {}", existing.target, e.message)
            throw e
        } catch (e: Exception) {
            log.error("Failed to update episode {} on {}: {}", episode.id, existing.target, e.message, e)
            publicationRepository.save(
                existing.copy(errorMessage = e.message)
            )
            throw e
        }
    }

    fun unpublish(episode: Episode, podcast: Podcast, userId: String, target: String): EpisodePublication {
        val publisher = publisherRegistry.getPublisher(target)
            ?: throw IllegalArgumentException("Unsupported publish target: $target")

        val publication = publicationRepository.findByEpisodeIdAndTarget(episode.id!!, target)
            ?: throw IllegalStateException("No publication found for episode ${episode.id} on $target")

        if (publication.status != PublicationStatus.PUBLISHED) {
            throw IllegalStateException("Publication is not in PUBLISHED status (current: ${publication.status})")
        }

        try {
            if (publication.externalId != null) {
                publisher.unpublish(userId, publication.externalId)
            }
            if (target == FtpPublisher.TARGET_NAME && episode.audioFilePath != null) {
                val audioFileName = java.nio.file.Path.of(episode.audioFilePath).fileName.toString()
                (publisher as FtpPublisher).deleteRemoteFile(userId, podcast.id, audioFileName)
            }
            log.info("Unpublished episode {} from {}", episode.id, target)
        } catch (e: UnsupportedOperationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Failed to remove episode {} from {}: {} — marking as unpublished anyway", episode.id, target, e.message)
        }

        val updated = publicationRepository.save(
            publication.copy(
                status = PublicationStatus.UNPUBLISHED,
                externalId = null
            )
        )

        if (target == SoundCloudPublisher.TARGET_NAME) {
            try {
                rebuildSoundCloudPlaylist(podcast, userId)
            } catch (e: Exception) {
                log.warn("Failed to rebuild SoundCloud playlist after unpublish: {}", e.message)
            }
        }

        publisher.postPublish(podcast, userId)
        staticFeedExporter.export(podcast)

        eventPublisher.publishEvent(
            PodcastEvent(this, podcast.id, "publication", episode.id, "episode.unpublished",
                mapOf("episodeNumber" to episode.id, "target" to target))
        )

        return updated
    }

    fun listByPodcast(
        podcastId: String,
        pageable: org.springframework.data.domain.Pageable
    ): org.springframework.data.domain.Page<PodcastPublicationRow> {
        val episodes = episodeRepository.findByPodcastId(podcastId)
        if (episodes.isEmpty()) {
            return org.springframework.data.domain.PageImpl(emptyList(), pageable, 0)
        }
        val episodesById = episodes.associateBy { it.id!! }
        val publications = publicationRepository.findByEpisodeIdIn(episodesById.keys, pageable)
        return publications.map { pub ->
            val ep = episodesById[pub.episodeId]
            PodcastPublicationRow(
                publication = pub.toResponse(),
                episode = PublicationEpisodeRef(
                    id = pub.episodeId,
                    generatedAt = ep?.generatedAt ?: "",
                    status = ep?.status?.name ?: ""
                )
            )
        }
    }

    fun getPublications(episodeId: Long): List<EpisodePublication> =
        publicationRepository.findByEpisodeId(episodeId)

    fun rebuildSoundCloudPlaylist(podcast: Podcast, userId: String): List<Long> {
        val publications = publicationRepository.findPublishedByPodcastIdAndTarget(podcast.id, SoundCloudPublisher.TARGET_NAME)
        require(publications.isNotEmpty()) { "No published SoundCloud tracks found for this podcast" }

        val episodeIds = publications.map { it.episodeId }.distinct()
        val episodes = episodeIds.mapNotNull { episodeRepository.findByIdOrNull(it) }
        val episodeById = episodes.associateBy { it.id }

        // Newest first — standard podcast playlist order
        val sortedPublications = publications
            .sortedByDescending { episodeById[it.episodeId]?.generatedAt ?: "" }
        val trackIds = sortedPublications.mapNotNull { it.externalId?.toLongOrNull() }
        require(trackIds.isNotEmpty()) { "No valid SoundCloud track IDs found" }

        val staleTrackIds = soundCloudPublisher.updateTrackPermalinks(podcast, userId, episodes, publications)
        val activeTrackIds = trackIds.filter { it !in staleTrackIds }
        require(activeTrackIds.isNotEmpty()) { "No active SoundCloud tracks found after filtering stale tracks" }
        soundCloudPublisher.rebuildPlaylist(podcast, userId, activeTrackIds)
        log.info("Updated permalinks and rebuilt SoundCloud playlist for podcast {} with {} tracks ({} stale skipped)", podcast.id, activeTrackIds.size, staleTrackIds.size)
        return trackIds
    }
}
