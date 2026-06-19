package com.aisummarypodcast.publishing

import com.aisummarypodcast.podcast.EpisodeService
import com.aisummarypodcast.podcast.PodcastEvent
import com.aisummarypodcast.podcast.PodcastService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Publishes a freshly generated episode to every publication target that is both `enabled` and
 * opted into `autoPublish`. Reacts to the existing `episode.generated` event so the audio/TTS layer
 * stays decoupled from publishing. Each target is published independently on a [Dispatchers.IO]
 * coroutine: one target failing does not block the others, and `PublishingService.publish` already
 * records a `FAILED` publication and emits a failure event, so here we only log.
 */
@Component
class AutoPublishListener(
    private val podcastService: PodcastService,
    private val episodeService: EpisodeService,
    private val targetService: PodcastPublicationTargetService,
    private val publishingService: PublishingService
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @EventListener
    fun onEpisodeGenerated(event: PodcastEvent) {
        if (event.event != "episode.generated") return

        val podcastId = event.podcastId
        val episodeId = event.entityId

        scope.launch {
            val autoTargets = targetService.list(podcastId).filter { it.enabled && it.autoPublish }
            if (autoTargets.isEmpty()) return@launch

            val podcast = podcastService.findById(podcastId)
            if (podcast == null) {
                log.warn("Auto-publish skipped: podcast {} not found for episode {}", podcastId, episodeId)
                return@launch
            }
            val episode = episodeService.findById(episodeId)
            if (episode == null) {
                log.warn("Auto-publish skipped: episode {} not found for podcast {}", episodeId, podcastId)
                return@launch
            }

            for (target in autoTargets) {
                try {
                    log.info("Auto-publishing episode {} to {} for podcast {}", episodeId, target.target, podcastId)
                    publishingService.publish(episode, podcast, podcast.userId, target.target)
                } catch (e: Exception) {
                    log.warn("Auto-publish of episode {} to {} failed: {}", episodeId, target.target, e.message)
                }
            }
        }
    }
}
