package com.aisummarypodcast.podcast

import com.aisummarypodcast.store.EpisodeRepository
import com.aisummarypodcast.store.EpisodeStatus
import com.aisummarypodcast.store.PodcastRepository
import com.aisummarypodcast.tts.TtsPipeline
import jakarta.annotation.PreDestroy
import org.springframework.data.repository.findByIdOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class AudioGenerationService(
    private val episodeRepository: EpisodeRepository,
    private val podcastRepository: PodcastRepository,
    private val ttsPipeline: TtsPipeline,
    private val eventPublisher: ApplicationEventPublisher
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @PreDestroy
    fun stop() {
        scope.cancel()
    }

    fun generateAudioAsync(episodeId: Long, podcastId: String) {
        scope.launch {
            doGenerateAudio(episodeId, podcastId)
        }
    }

    fun regenerateAudioAsync(episodeId: Long, podcastId: String) {
        scope.launch {
            doGenerateAudio(episodeId, podcastId)
        }
    }

    internal suspend fun doGenerateAudio(episodeId: Long, podcastId: String) {
        val episode = episodeRepository.findByIdOrNull(episodeId)
        if (episode == null) {
            log.error("Episode {} not found for async TTS generation", episodeId)
            return
        }

        val podcast = podcastRepository.findByIdOrNull(podcastId)
        if (podcast == null) {
            log.error("Podcast {} not found for async TTS generation", podcastId)
            episodeRepository.save(episode.copy(status = EpisodeStatus.FAILED, errorMessage = "Podcast not found"))
            return
        }

        try {
            log.info("Starting async TTS generation for episode {} (podcast '{}' ({}))", episodeId, podcast.name, podcastId)
            val fresh = episodeRepository.findByIdOrNull(episodeId) ?: episode
            val generatingEpisode = episodeRepository.save(fresh.copy(status = EpisodeStatus.GENERATING_AUDIO))
            eventPublisher.publishEvent(
                PodcastEvent(this, podcastId, "episode", episodeId, "episode.audio.started",
                    mapOf("episodeNumber" to episodeId))
            )
            ttsPipeline.generateForExistingEpisode(generatingEpisode, podcast)
            log.info("Async TTS generation complete for episode {} (podcast '{}' ({}))", episodeId, podcast.name, podcastId)
            eventPublisher.publishEvent(
                PodcastEvent(this, podcastId, "episode", episodeId, "episode.generated",
                    mapOf("episodeNumber" to episodeId))
            )
        } catch (e: Exception) {
            log.error("Async TTS generation failed for episode {} (podcast '{}' ({})): {}", episodeId, podcast.name, podcastId, e.message, e)
            val freshEpisode = episodeRepository.findByIdOrNull(episodeId) ?: episode
            episodeRepository.save(freshEpisode.copy(status = EpisodeStatus.FAILED, errorMessage = e.message, pipelineStage = "tts"))
            eventPublisher.publishEvent(
                PodcastEvent(this, podcastId, "episode", episodeId, "episode.failed",
                    mapOf("episodeNumber" to episodeId, "error" to (e.message ?: "Unknown error")))
            )
        }
    }
}
