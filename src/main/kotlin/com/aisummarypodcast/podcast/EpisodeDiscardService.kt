package com.aisummarypodcast.podcast

import com.aisummarypodcast.publishing.PublishingService
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodeStatus
import org.springframework.stereotype.Service

/**
 * Guards discard against removing an episode that is still live on a publication target.
 *
 * The guard cannot live in [EpisodeService] itself: that would make it depend on
 * [PublishingService], which depends on `PublisherRegistry` -> `FtpPublisher` -> `EpisodeService`,
 * a bean cycle. Sitting one layer above both services breaks that cycle while keeping the check
 * in one place.
 */
@Service
class EpisodeDiscardService(
    private val episodeService: EpisodeService,
    private val publishingService: PublishingService
) {

    fun discard(episode: Episode, podcastId: String) {
        if (episode.status !in DISCARDABLE_STATUSES) {
            throw EpisodeStatusNotAllowedException(
                if (episode.status == EpisodeStatus.GENERATING_AUDIO) "Audio generation is in progress"
                else "Episode is not in a discardable status"
            )
        }
        val liveTargets = publishingService.liveTargets(episode.id!!)
        if (liveTargets.isNotEmpty()) {
            throw EpisodePublishedException(
                "Episode is published to ${liveTargets.joinToString(", ")}; unpublish it first"
            )
        }

        if (episode.status == EpisodeStatus.FAILED) {
            episodeService.discardOnly(episode, podcastId)
        } else {
            episodeService.discardAndResetArticles(episode, podcastId)
        }
    }

    private companion object {
        val DISCARDABLE_STATUSES = setOf(EpisodeStatus.PENDING_REVIEW, EpisodeStatus.GENERATED, EpisodeStatus.FAILED)
    }
}
