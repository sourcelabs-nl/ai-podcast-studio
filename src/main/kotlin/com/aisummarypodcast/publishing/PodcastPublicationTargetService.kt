package com.aisummarypodcast.publishing

import com.aisummarypodcast.store.PodcastPublicationTarget
import com.aisummarypodcast.store.PodcastPublicationTargetRepository
import org.springframework.stereotype.Service

@Service
class PodcastPublicationTargetService(
    private val repository: PodcastPublicationTargetRepository
) {

    fun list(podcastId: String): List<PodcastPublicationTarget> =
        repository.findByPodcastId(podcastId)

    fun get(podcastId: String, target: String): PodcastPublicationTarget? =
        repository.findByPodcastIdAndTarget(podcastId, target)

    fun upsert(
        podcastId: String,
        target: String,
        config: String,
        enabled: Boolean,
        autoPublish: Boolean
    ): PodcastPublicationTarget {
        return repository.upsert(
            PodcastPublicationTarget(
                podcastId = podcastId,
                target = target,
                config = config,
                enabled = enabled,
                autoPublish = autoPublish
            )
        )
    }

    fun delete(podcastId: String, target: String): Boolean =
        repository.deleteByPodcastIdAndTarget(podcastId, target) > 0
}
