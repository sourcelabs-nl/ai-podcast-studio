package com.aisummarypodcast.publishing

import com.aisummarypodcast.api.InvalidRequestException
import com.aisummarypodcast.api.OperationFailedException
import com.aisummarypodcast.podcast.StaticFeedExporter
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodePublication
import com.aisummarypodcast.store.EpisodePublicationRepository
import com.aisummarypodcast.store.EpisodeRepository
import com.aisummarypodcast.store.EpisodePurpose
import com.aisummarypodcast.store.EpisodeStatus
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.PublicationStatus
import com.aisummarypodcast.podcast.PodcastEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        requirePublishable(episode, podcast, target)

        val existing = publicationRepository.findByEpisodeIdAndTarget(episode.id!!, target)
        if (existing?.status == PublicationStatus.PUBLISHED && existing.externalId != null) {
            return updateExisting(publisher, episode, podcast, userId, existing)
        }

        replaceSameDayPublications(publisher, episode, podcast, userId, target)
        val claim = claimPublication(episode, target, existing)
        val result = upload(publisher, episode, podcast, userId, claim)
        return recordPublished(publisher, episode, podcast, userId, claim, result)
    }

    /** Throws when the target is not enabled for the podcast or the episode is not in a state to be published. */
    private fun requirePublishable(episode: Episode, podcast: Podcast, target: String) {
        val publicationTarget = targetService.get(podcast.id, target)
        if (publicationTarget == null || !publicationTarget.enabled) {
            throw TargetNotConfiguredException("Publication target '$target' is not configured or enabled for this podcast")
        }

        if (episode.purpose == EpisodePurpose.EXPERIMENT) {
            throw IllegalStateException("Episode ${episode.id} is an experiment and is never published")
        }

        if (episode.status != EpisodeStatus.GENERATED) {
            throw IllegalStateException("Episode must be in GENERATED status to publish (current: ${episode.status})")
        }

        if (podcast.requirePublishApproval && !episode.publishApproved) {
            throw PublishApprovalRequiredException("Episode must be approved for publication before it can be published")
        }

        if (episode.audioFilePath == null) {
            throw IllegalStateException("Episode has no audio file")
        }
    }

    /**
     * Replaces any existing publication from another episode of the same kind with the same date
     * (regenerated episodes). A focus episode is an extra next to the regular one, so the two never
     * replace each other.
     */
    private suspend fun replaceSameDayPublications(
        publisher: EpisodePublisher,
        episode: Episode,
        podcast: Podcast,
        userId: String,
        target: String
    ) {
        val episodeDate = episode.generatedAt.substring(0, 10) // YYYY-MM-DD
        val previousPublications = publicationRepository.findPublishedByPodcastIdAndTarget(podcast.id, target)
        for (prev in previousPublications) {
            if (prev.episodeId == episode.id) continue
            val prevEpisode = episodeRepository.findByIdOrNull(prev.episodeId) ?: continue
            val prevDate = prevEpisode.generatedAt.substring(0, 10)
            if (prevDate != episodeDate) continue
            if ((prevEpisode.focus != null) != (episode.focus != null)) continue
            // The record is the only thing that names the remote asset, so it is deleted only once the
            // asset is gone. A failed removal keeps it (with the reason) and the new publication goes ahead.
            val removed = try {
                if (prev.externalId != null) {
                    withContext(Dispatchers.IO) { publisher.unpublish(userId, prev.externalId) }
                }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("Failed to unpublish previous episode {} from {}: {}; keeping its publication record", prev.episodeId, target, e.message)
                publicationRepository.save(prev.copy(errorMessage = e.message))
                false
            }
            if (removed) {
                publicationRepository.delete(prev)
                log.info("Replaced same-day publication (episode {}, externalId={}) on {}", prev.episodeId, prev.externalId, target)
            }
        }
    }

    /** Saves the PENDING row that marks this publish as in flight, reusing the id and creation time of an earlier attempt. */
    private fun claimPublication(episode: Episode, target: String, existing: EpisodePublication?): EpisodePublication =
        publicationRepository.save(
            EpisodePublication(
                id = existing?.id,
                episodeId = episode.id!!,
                target = target,
                status = PublicationStatus.PENDING,
                createdAt = existing?.createdAt ?: Instant.now().toString()
            )
        )

    /**
     * Only the upload itself decides whether this publication failed. Everything after it runs
     * outside the catch, so a broken side effect can no longer stamp FAILED over a row that
     * holds a live external id.
     */
    private suspend fun upload(
        publisher: EpisodePublisher,
        episode: Episode,
        podcast: Podcast,
        userId: String,
        claim: EpisodePublication
    ): PublishResult {
        val target = claim.target
        return try {
            log.info("Publishing episode {} to {}", episode.id, target)
            publisher.publish(episode, podcast, userId)
        } catch (e: CancellationException) {
            // A cancelled call proves nothing about whether the upload landed, so the claim is left
            // PENDING rather than recorded as a failure.
            log.warn("Publishing episode {} to {} was cancelled; leaving the publication PENDING", episode.id, target)
            throw e
        } catch (e: Exception) {
            log.error("Failed to publish episode {} to {}: {}", episode.id, target, e.message, e)
            publicationRepository.save(
                claim.copy(
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

    /** Marks the claim PUBLISHED with the upload's external id, then runs the side effects and announces it. */
    private suspend fun recordPublished(
        publisher: EpisodePublisher,
        episode: Episode,
        podcast: Podcast,
        userId: String,
        claim: EpisodePublication,
        result: PublishResult
    ): EpisodePublication {
        val target = claim.target
        val published = publicationRepository.save(
            claim.copy(
                status = PublicationStatus.PUBLISHED,
                externalId = result.externalId,
                externalUrl = result.externalUrl,
                publishedAt = Instant.now().toString(),
                errorMessage = null
            )
        )
        log.info("Episode {} published to {} (externalId={})", episode.id, target, result.externalId)

        runPostPublishSideEffects(publisher, podcast, userId, target)
        eventPublisher.publishEvent(
            PodcastEvent(this, podcast.id, "publication", episode.id!!, "episode.published",
                mapOf("episodeNumber" to episode.id, "target" to target))
        )
        return published
    }

    /**
     * The bookkeeping that follows a successful publish or update: the publisher's own hook, the
     * SoundCloud playlist, and the static feed.
     *
     * None of it can undo the upload, so a failure here is logged and the publication keeps the
     * external id it just earned. Marking the row FAILED instead is what lost episode 202's
     * SoundCloud track id: the upload had succeeded, a later step was cancelled, and the catch wrote
     * a stale pre-publish snapshot over the row. With the id gone, the next same-day publish could
     * not clean up the old track and SoundCloud rejected the new one over a taken permalink.
     */
    private suspend fun runPostPublishSideEffects(
        publisher: EpisodePublisher,
        podcast: Podcast,
        userId: String,
        target: String
    ) {
        runSideEffect(target, "Post-publish hook") { publisher.postPublish(podcast, userId) }
        if (target == SoundCloudPublisher.TARGET_NAME) {
            runSideEffect(target, "SoundCloud playlist rebuild") { rebuildSoundCloudPlaylist(podcast, userId) }
        }
        runSideEffect(target, "Static feed export") { staticFeedExporter.export(podcast) }
    }

    private suspend fun runSideEffect(target: String, description: String, block: () -> Unit) {
        try {
            withContext(Dispatchers.IO) { block() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("{} failed after publishing to {}: {}", description, target, e.message)
        }
    }

    private suspend fun updateExisting(
        publisher: EpisodePublisher,
        episode: Episode,
        podcast: Podcast,
        userId: String,
        existing: EpisodePublication
    ): EpisodePublication {
        val result = try {
            log.info("Updating episode {} on {} (externalId={})", episode.id, existing.target, existing.externalId)
            publisher.update(episode, podcast, userId, existing.externalId!!)
        } catch (e: UnsupportedOperationException) {
            log.warn("Publisher {} does not support updates: {}", existing.target, e.message)
            throw e
        } catch (e: CancellationException) {
            log.warn("Updating episode {} on {} was cancelled; leaving the publication untouched", episode.id, existing.target)
            throw e
        } catch (e: Exception) {
            log.error("Failed to update episode {} on {}: {}", episode.id, existing.target, e.message, e)
            publicationRepository.save(existing.copy(errorMessage = e.message))
            throw e
        }

        // Persist the id the publisher came back with, not the one we went in with. An update is
        // not obliged to keep the same external identity: SoundCloud cannot replace a track's
        // audio in place so it returns a new track id, and the FTP id is derived from the audio
        // filename so it changes whenever the episode is re-synthesized. Keeping the old id left
        // the row pointing at a track that no longer exists, or at a superseded MP3 filename.
        val updated = publicationRepository.save(
            existing.copy(
                externalId = result.externalId,
                externalUrl = result.externalUrl,
                publishedAt = Instant.now().toString(),
                errorMessage = null
            )
        )
        log.info("Episode {} updated on {} (externalId={})", episode.id, existing.target, result.externalId)

        runPostPublishSideEffects(publisher, podcast, userId, existing.target)
        return updated
    }

    fun unpublish(episode: Episode, podcast: Podcast, userId: String, target: String): EpisodePublication {
        val publisher = publisherRegistry.getPublisher(target)
            ?: throw IllegalArgumentException("Unsupported publish target: $target")

        val publication = publicationRepository.findByEpisodeIdAndTarget(episode.id!!, target)
            ?: throw NoPublicationFoundException("No publication found for episode ${episode.id} on $target")

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
            log.warn("Failed to remove episode {} from {}: {}; keeping its publication record", episode.id, target, e.message)
            publicationRepository.save(publication.copy(errorMessage = e.message))
            throw e
        }

        val updated = publicationRepository.save(
            publication.copy(
                status = PublicationStatus.UNPUBLISHED,
                externalId = null,
                errorMessage = null
            )
        )

        // The asset is gone, so a failure in the follow-up work cannot change the outcome.
        if (target == SoundCloudPublisher.TARGET_NAME) {
            logUnpublishSideEffectFailure("SoundCloud playlist rebuild") { rebuildSoundCloudPlaylist(podcast, userId) }
        }
        logUnpublishSideEffectFailure("Post-publish hook") { publisher.postPublish(podcast, userId) }
        logUnpublishSideEffectFailure("Static feed export") { staticFeedExporter.export(podcast) }

        eventPublisher.publishEvent(
            PodcastEvent(this, podcast.id, "publication", episode.id, "episode.unpublished",
                mapOf("episodeNumber" to episode.id, "target" to target))
        )

        return updated
    }

    private fun logUnpublishSideEffectFailure(description: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            log.warn("{} failed after unpublishing: {}", description, e.message)
        }
    }

    /** A page of the podcast's publications, newest first (the order is fixed by the query, not by [pageable]'s sort). */
    fun listByPodcast(
        podcastId: String,
        pageable: org.springframework.data.domain.Pageable
    ): org.springframework.data.domain.Page<PodcastPublicationRow> {
        val publications = publicationRepository.findPageByPodcastId(podcastId, pageable.pageSize, pageable.offset)
        val episodesById = episodeRepository.findAllById(publications.map { it.episodeId }.distinct()).associateBy { it.id!! }
        val rows = publications.map { pub ->
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
        return org.springframework.data.domain.PageImpl(rows, pageable, publicationRepository.countByPodcastId(podcastId))
    }

    fun getPublications(episodeId: Long): List<EpisodePublication> =
        publicationRepository.findByEpisodeId(episodeId)

    /** Targets [episodeId] is still live on, i.e. published and not yet unpublished. */
    fun liveTargets(episodeId: Long): List<String> =
        publicationRepository.findByEpisodeId(episodeId)
            .filter { it.status == PublicationStatus.PUBLISHED }
            .map { it.target }

    /**
     * Updates the permalinks of the podcast's published SoundCloud tracks and rebuilds the playlist.
     *
     * @throws InvalidRequestException when the podcast has no usable published track
     * @throws OperationFailedException when SoundCloud or the bookkeeping fails
     */
    fun rebuildSoundCloudPlaylist(podcast: Podcast, userId: String): List<Long> {
        val publications = publicationRepository.findPublishedByPodcastIdAndTarget(podcast.id, SoundCloudPublisher.TARGET_NAME)
        if (publications.isEmpty()) throw InvalidRequestException("No published SoundCloud tracks found for this podcast")

        // publications already arrive newest-first (ordered by episode generatedAt in the query),
        // which is the standard podcast playlist order
        val trackIds = publications.mapNotNull { it.externalId?.toLongOrNull() }
        if (trackIds.isEmpty()) throw InvalidRequestException("No valid SoundCloud track IDs found")

        try {
            return updatePermalinksAndRebuild(podcast, userId, publications, trackIds)
        } catch (e: InvalidRequestException) {
            throw e
        } catch (e: Exception) {
            log.error("Failed to rebuild playlist for podcast {}: {}", podcast.id, e.message, e)
            throw OperationFailedException("Failed to rebuild playlist: ${e.message}", e)
        }
    }

    private fun updatePermalinksAndRebuild(
        podcast: Podcast,
        userId: String,
        publications: List<EpisodePublication>,
        trackIds: List<Long>
    ): List<Long> {
        val episodeIds = publications.map { it.episodeId }.distinct()
        val episodes = episodeIds.mapNotNull { episodeRepository.findByIdOrNull(it) }

        val staleTrackIds = soundCloudPublisher.updateTrackPermalinks(podcast, userId, episodes, publications)
        val activeTrackIds = trackIds.filter { it !in staleTrackIds }
        if (activeTrackIds.isEmpty()) {
            throw InvalidRequestException("No active SoundCloud tracks found after filtering stale tracks")
        }
        soundCloudPublisher.rebuildPlaylist(podcast, userId, activeTrackIds)
        markStaleTracksUnpublished(publications, staleTrackIds)
        log.info("Updated permalinks and rebuilt SoundCloud playlist for podcast {} with {} tracks ({} stale skipped)", podcast.id, activeTrackIds.size, staleTrackIds.size)
        return trackIds
    }

    /**
     * Records that [staleTrackIds] no longer exist on SoundCloud, so a later rebuild stops asking
     * about them.
     *
     * Freeing upload quota deletes this podcast's oldest tracks without touching their publication
     * rows, so those rows keep a `PUBLISHED` status and a dead track id forever. Every rebuild then
     * re-requested each one and logged a 404: 101 dead ids produced 1,180 warnings over eight days,
     * drowning the log in noise that hid real failures. A 404 from SoundCloud is proof the track is
     * gone, which makes the episode genuinely no longer published there, so the row is corrected to
     * say so and the user can republish if they want it back.
     *
     * Deliberately not `@Transactional`: the caller holds no transaction because it makes SoundCloud
     * calls, and each row here is an independent, idempotent correction. A partial failure leaves
     * the remaining rows for the next rebuild to fix.
     */
    private fun markStaleTracksUnpublished(
        publications: List<EpisodePublication>,
        staleTrackIds: Set<Long>
    ) {
        if (staleTrackIds.isEmpty()) return

        val stale = publications.filter { it.externalId?.toLongOrNull() in staleTrackIds }
        for (publication in stale) {
            publicationRepository.save(
                publication.copy(status = PublicationStatus.UNPUBLISHED, externalId = null)
            )
        }
        log.info(
            "Marked {} SoundCloud publication(s) unpublished for podcast tracks that no longer exist",
            stale.size
        )
    }
}
