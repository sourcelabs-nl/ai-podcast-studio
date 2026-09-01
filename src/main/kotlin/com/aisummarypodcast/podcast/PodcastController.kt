package com.aisummarypodcast.podcast

import com.aisummarypodcast.config.LlmModelOverrides
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.PodcastStyle
import com.aisummarypodcast.store.Subtopics
import com.aisummarypodcast.store.TtsProviderType
import com.aisummarypodcast.user.UserService
import tools.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.URI
import java.time.ZoneId

@RestController
@RequestMapping("/users/{userId}/podcasts")
class PodcastController(
    private val podcastService: PodcastService,
    private val userService: UserService,
    private val episodeService: EpisodeService,
    private val objectMapper: ObjectMapper,
    private val pipelineStateTracker: PipelineStateTracker
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val previewScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @PreDestroy
    fun onDestroy() {
        previewScope.cancel()
    }

    @PostMapping
    fun create(@PathVariable userId: String, @RequestBody request: CreatePodcastRequest): ResponseEntity<Any> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        val language = request.language ?: "en"
        if (!SupportedLanguage.isSupported(language)) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Unsupported language: $language"))
        }
        val ttsProvider = request.ttsProvider?.let {
            TtsProviderType.fromValue(it)
                ?: return ResponseEntity.badRequest().body(mapOf("error" to "Unsupported TTS provider: $it. Supported: ${TtsProviderType.entries.joinToString { e -> e.value }}"))
        } ?: TtsProviderType.OPENAI
        val style = request.style?.let {
            PodcastStyle.fromValue(it)
                ?: return ResponseEntity.badRequest().body(mapOf("error" to "Unsupported style: $it. Supported: ${PodcastStyle.entries.joinToString { e -> e.value }}"))
        } ?: PodcastStyle.NEWS_BRIEFING
        podcastService.validateTtsConfig(ttsProvider, style, request.ttsVoices)?.let {
            return ResponseEntity.badRequest().body(mapOf("error" to it))
        }
        if (request.timezone != null) {
            try { ZoneId.of(request.timezone) } catch (_: Exception) {
                return ResponseEntity.badRequest().body(mapOf("error" to "Invalid timezone: ${request.timezone}"))
            }
        }
        if (request.fullBodyThreshold != null && request.fullBodyThreshold < 1) {
            return ResponseEntity.badRequest().body(mapOf("error" to "fullBodyThreshold must be at least 1"))
        }
        validateComposeSettings(request.composeSettings)?.let { return it }
        validateSubtopics(request.subtopics)?.let { return it }
        validateRapidFireThreshold(request.rapidFireWeightThreshold)?.let { return it }
        validateRapidFireMaxItems(request.rapidFireMaxItems)?.let { return it }
        val podcast = podcastService.create(
            userId = userId,
            name = request.name,
            topic = request.topic,
            podcast = Podcast(
                id = "",
                userId = userId,
                name = request.name,
                topic = request.topic,
                language = language,
                llmModels = request.llmModels?.let { LlmModelOverrides(it) },
                ttsProvider = ttsProvider,
                ttsVoices = request.ttsVoices,
                ttsSettings = request.ttsSettings,
                style = style,
                targetWords = request.targetWords,
                cron = request.cron ?: "0 0 6 * * *",
                timezone = request.timezone ?: "UTC",
                customInstructions = request.customInstructions,
                relevanceThreshold = request.relevanceThreshold ?: 5,
                requireReview = request.requireReview ?: false,
                requirePublishApproval = request.requirePublishApproval ?: false,
                maxLlmCostCents = request.maxLlmCostCents,
                maxArticleAgeDays = request.maxArticleAgeDays,
                speakerNames = request.speakerNames,
                fullBodyThreshold = request.fullBodyThreshold,
                sponsor = request.sponsor,
                pronunciations = request.pronunciations,
                recapLookbackEpisodes = request.recapLookbackEpisodes,
                composeSettings = request.composeSettings,
                deepDiveEnabled = request.deepDiveEnabled ?: false,
                subtopics = request.subtopics?.takeIf { it.isNotEmpty() }?.let { Subtopics(it) },
                rapidFireWeightThreshold = request.rapidFireWeightThreshold ?: 3,
                rapidFireMaxItems = request.rapidFireMaxItems
            )
        )
        return ResponseEntity.created(URI.create("/users/$userId/podcasts/${podcast.id}"))
            .body(podcast.toResponse())
    }

    @GetMapping
    fun list(@PathVariable userId: String): ResponseEntity<List<PodcastResponse>> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(podcastService.findByUserId(userId).map { it.toResponse() })
    }

    @GetMapping("/{podcastId}")
    fun get(@PathVariable userId: String, @PathVariable podcastId: String): ResponseEntity<PodcastResponse> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        val podcast = podcastService.findById(podcastId) ?: return ResponseEntity.notFound().build()
        if (podcast.userId != userId) return ResponseEntity.notFound().build()
        return ResponseEntity.ok(podcast.toResponse())
    }

    @PutMapping("/{podcastId}")
    fun update(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @RequestBody request: UpdatePodcastRequest
    ): ResponseEntity<Any> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        val existing = podcastService.findById(podcastId) ?: return ResponseEntity.notFound().build()
        if (existing.userId != userId) return ResponseEntity.notFound().build()
        if (request.language != null && !SupportedLanguage.isSupported(request.language)) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Unsupported language: ${request.language}"))
        }
        val effectiveTtsProvider = request.ttsProvider?.let {
            TtsProviderType.fromValue(it)
                ?: return ResponseEntity.badRequest().body(mapOf("error" to "Unsupported TTS provider: $it. Supported: ${TtsProviderType.entries.joinToString { e -> e.value }}"))
        } ?: existing.ttsProvider
        val effectiveStyle = request.style?.let {
            PodcastStyle.fromValue(it)
                ?: return ResponseEntity.badRequest().body(mapOf("error" to "Unsupported style: $it. Supported: ${PodcastStyle.entries.joinToString { e -> e.value }}"))
        } ?: existing.style
        val effectiveVoices = request.ttsVoices.orKeep(existing.ttsVoices)
        podcastService.validateTtsConfig(effectiveTtsProvider, effectiveStyle, effectiveVoices)?.let {
            return ResponseEntity.badRequest().body(mapOf("error" to it))
        }
        if (request.timezone != null) {
            try { ZoneId.of(request.timezone) } catch (_: Exception) {
                return ResponseEntity.badRequest().body(mapOf("error" to "Invalid timezone: ${request.timezone}"))
            }
        }
        if (request.fullBodyThreshold != null && request.fullBodyThreshold < 1) {
            return ResponseEntity.badRequest().body(mapOf("error" to "fullBodyThreshold must be at least 1"))
        }
        validateComposeSettings(request.composeSettings)?.let { return it }
        validateSubtopics(request.subtopics)?.let { return it }
        validateRapidFireThreshold(request.rapidFireWeightThreshold)?.let { return it }
        validateRapidFireMaxItems(request.rapidFireMaxItems)?.let { return it }
        val updated = podcastService.update(
            podcastId,
            existing.copy(
                name = request.name,
                topic = request.topic,
                language = request.language ?: existing.language,
                llmModels = request.llmModels.toLlmModelOverrides(existing.llmModels),
                ttsProvider = effectiveTtsProvider,
                ttsVoices = request.ttsVoices.orKeep(existing.ttsVoices),
                ttsSettings = request.ttsSettings.orKeep(existing.ttsSettings),
                style = effectiveStyle,
                targetWords = request.targetWords,
                cron = request.cron ?: existing.cron,
                timezone = request.timezone ?: existing.timezone,
                customInstructions = request.customInstructions.orKeep(existing.customInstructions),
                relevanceThreshold = request.relevanceThreshold ?: existing.relevanceThreshold,
                requireReview = request.requireReview ?: existing.requireReview,
                requirePublishApproval = request.requirePublishApproval ?: existing.requirePublishApproval,
                maxLlmCostCents = request.maxLlmCostCents,
                maxArticleAgeDays = request.maxArticleAgeDays,
                speakerNames = request.speakerNames.orKeep(existing.speakerNames),
                fullBodyThreshold = request.fullBodyThreshold,
                sponsor = request.sponsor.orKeep(existing.sponsor),
                pronunciations = request.pronunciations.orKeep(existing.pronunciations),
                recapLookbackEpisodes = request.recapLookbackEpisodes,
                composeSettings = request.composeSettings.orKeep(existing.composeSettings),
                deepDiveEnabled = request.deepDiveEnabled ?: existing.deepDiveEnabled,
                subtopics = request.subtopics.toSubtopics(existing.subtopics),
                rapidFireWeightThreshold = request.rapidFireWeightThreshold ?: existing.rapidFireWeightThreshold,
                rapidFireMaxItems = request.rapidFireMaxItems
            )
        ) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(updated.toResponse())
    }

    @DeleteMapping("/{podcastId}")
    fun delete(@PathVariable userId: String, @PathVariable podcastId: String): ResponseEntity<Void> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        val podcast = podcastService.findById(podcastId) ?: return ResponseEntity.notFound().build()
        if (podcast.userId != userId) return ResponseEntity.notFound().build()
        podcastService.delete(podcastId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{podcastId}/pipeline-status")
    fun pipelineStatus(@PathVariable userId: String, @PathVariable podcastId: String): ResponseEntity<Any> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        val podcast = podcastService.findById(podcastId) ?: return ResponseEntity.notFound().build()
        if (podcast.userId != userId) return ResponseEntity.notFound().build()

        val stage = pipelineStateTracker.getStage(podcastId)
        return ResponseEntity.ok(mapOf("stage" to stage))
    }

    @PostMapping("/{podcastId}/generate")
    fun generate(@PathVariable userId: String, @PathVariable podcastId: String): ResponseEntity<Any> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        val podcast = podcastService.findById(podcastId) ?: return ResponseEntity.notFound().build()
        if (podcast.userId != userId) return ResponseEntity.notFound().build()

        log.info("Manual briefing generation triggered for podcast {}", podcastId)

        // Generation runs in the background (it can take minutes); we return 202 with the GENERATING
        // episode id and the UI follows progress via SSE. Running it inline in the request would tie
        // it to the async-request timeout, which would cancel the in-flight pipeline.
        val episode = podcastService.generateBriefingAsync(podcast)
            ?: return ResponseEntity.status(409).body(mapOf("message" to "An episode is already generating for this podcast"))

        return ResponseEntity.accepted().body(mapOf("message" to "Generation started", "episodeId" to episode.id))
    }

    @PostMapping("/{podcastId}/episodes/{episodeId}/regenerate")
    fun regenerate(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable episodeId: Long
    ): ResponseEntity<Any> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        val podcast = podcastService.findById(podcastId) ?: return ResponseEntity.notFound().build()
        if (podcast.userId != userId) return ResponseEntity.notFound().build()
        val episode = episodeService.findById(episodeId) ?: return ResponseEntity.notFound().build()
        if (episode.podcastId != podcastId) return ResponseEntity.notFound().build()

        log.info("Regenerate triggered for episode {} of podcast {}", episodeId, podcastId)
        // Background work (recompose + TTS); return 202 with the new GENERATING episode id.
        val newEpisode = podcastService.regenerateEpisodeAsync(episode, podcast)
        return ResponseEntity.accepted().body(mapOf("message" to "Episode regeneration started", "episodeId" to newEpisode.id))
    }

    @GetMapping("/{podcastId}/articles/{articleId}/posts")
    fun articlePosts(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable articleId: Long
    ): ResponseEntity<List<ArticlePostResponse>> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        val podcast = podcastService.findById(podcastId) ?: return ResponseEntity.notFound().build()
        if (podcast.userId != userId) return ResponseEntity.notFound().build()

        val posts = podcastService.findArticlePosts(podcast, articleId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(posts)
    }

    @GetMapping("/{podcastId}/upcoming-articles")
    fun upcomingArticles(@PathVariable userId: String, @PathVariable podcastId: String): ResponseEntity<Any> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        val podcast = podcastService.findById(podcastId) ?: return ResponseEntity.notFound().build()
        if (podcast.userId != userId) return ResponseEntity.notFound().build()

        val content = podcastService.getUpcomingContent(podcast)
        return ResponseEntity.ok(content.toResponse())
    }

    @GetMapping("/{podcastId}/preview", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun preview(@PathVariable userId: String, @PathVariable podcastId: String): Any {
        userService.findById(userId) ?: return ResponseEntity.notFound().build<Any>()
        val podcast = podcastService.findById(podcastId) ?: return ResponseEntity.notFound().build<Any>()
        if (podcast.userId != userId) return ResponseEntity.notFound().build<Any>()

        val emitter = SseEmitter(600_000L)

        emitter.onCompletion { log.debug("Preview SSE completed for podcast {}", podcastId) }
        emitter.onTimeout { log.warn("Preview SSE timed out for podcast {}", podcastId) }
        emitter.onError { e -> log.error("Preview SSE error for podcast {}: {}", podcastId, e.message) }

        log.info("Preview SSE requested for podcast {}", podcastId)

        previewScope.launch {
            val heartbeat = launch {
                while (isActive) {
                    delay(15_000L)
                    try {
                        emitter.send(SseEmitter.event().name("heartbeat").data("{}"))
                    } catch (e: Exception) {
                        log.debug("Heartbeat send failed for podcast {}: {}", podcastId, e.message)
                        break
                    }
                }
            }
            try {
                val result = podcastService.previewBriefing(podcast) { stage, detail ->
                    try {
                        emitter.send(
                            SseEmitter.event()
                                .name("progress")
                                .data(objectMapper.writeValueAsString(mapOf("stage" to stage) + detail))
                        )
                    } catch (e: Exception) {
                        log.debug("Failed to send progress event: {}", e.message)
                    }
                }

                if (result != null) {
                    emitter.send(
                        SseEmitter.event()
                            .name("result")
                            .data(objectMapper.writeValueAsString(mapOf(
                                "scriptText" to result.script,
                                "style" to podcast.style.value,
                                "articleIds" to result.articleIds
                            )))
                    )
                } else {
                    emitter.send(
                        SseEmitter.event()
                            .name("result")
                            .data(objectMapper.writeValueAsString(mapOf(
                                "message" to "No relevant articles available for preview"
                            )))
                    )
                }

                emitter.send(SseEmitter.event().name("complete").data(""))
                emitter.complete()
            } catch (e: Exception) {
                log.error("Preview pipeline failed for podcast {}: {}", podcastId, e.message, e)
                try {
                    emitter.send(
                        SseEmitter.event()
                            .name("error")
                            .data(objectMapper.writeValueAsString(mapOf("message" to (e.message ?: "Preview generation failed"))))
                    )
                    emitter.complete()
                } catch (sendError: Exception) {
                    emitter.completeWithError(e)
                }
            } finally {
                heartbeat.cancelAndJoin()
            }
        }

        return emitter
    }

    private fun validateSubtopics(subtopics: Map<String, Int>?): ResponseEntity<Any>? {
        if (subtopics == null) return null
        for ((name, weight) in subtopics) {
            if (name.isBlank()) {
                return ResponseEntity.badRequest().body(mapOf("error" to "Subtopic name must not be empty"))
            }
            if (weight < 1 || weight > 10) {
                return ResponseEntity.badRequest()
                    .body(mapOf("error" to "Subtopic '$name' weight must be in [1, 10], got $weight"))
            }
        }
        return null
    }

    private fun validateRapidFireThreshold(threshold: Int?): ResponseEntity<Any>? {
        if (threshold == null) return null
        if (threshold < 0 || threshold > 10) {
            return ResponseEntity.badRequest()
                .body(mapOf("error" to "rapidFireWeightThreshold must be in [0, 10], got $threshold"))
        }
        return null
    }

    private fun validateRapidFireMaxItems(maxItems: Int?): ResponseEntity<Any>? {
        if (maxItems == null) return null
        if (maxItems < 0 || maxItems > 50) {
            return ResponseEntity.badRequest()
                .body(mapOf("error" to "rapidFireMaxItems must be in [0, 50], got $maxItems"))
        }
        return null
    }

    private fun validateComposeSettings(composeSettings: Map<String, String>?): ResponseEntity<Any>? {
        val temperatureRaw = composeSettings?.get("temperature") ?: return null
        val temperature = temperatureRaw.toDoubleOrNull()
            ?: return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(mapOf("error" to "composeSettings.temperature must be a number"))
        if (temperature < 0.0 || temperature > 2.0) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(mapOf("error" to "composeSettings.temperature must be in [0.0, 2.0]"))
        }
        return null
    }

}
