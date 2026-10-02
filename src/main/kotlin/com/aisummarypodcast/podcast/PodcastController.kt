package com.aisummarypodcast.podcast

import com.aisummarypodcast.user.UserService
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.URI

@RestController
@RequestMapping("/users/{userId}/podcasts")
class PodcastController(
    private val podcastService: PodcastService,
    private val userService: UserService,
    private val podcastAccess: PodcastAccess,
    private val episodeService: EpisodeService,
    private val previewStreamer: PreviewStreamer,
    private val podcastValidator: PodcastValidator
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping
    fun create(@PathVariable userId: String, @RequestBody request: CreatePodcastRequest): ResponseEntity<Any> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        val podcast = request.toPodcast(userId)
        podcastValidator.validate(podcast)
        val created = podcastService.create(userId = userId, name = request.name, topic = request.topic, podcast = podcast)
        return ResponseEntity.created(URI.create("/users/$userId/podcasts/${created.id}"))
            .body(created.toResponse())
    }

    @GetMapping
    fun list(@PathVariable userId: String): ResponseEntity<List<PodcastResponse>> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(podcastService.findByUserId(userId).map { it.toResponse() })
    }

    @GetMapping("/{podcastId}")
    fun get(@PathVariable userId: String, @PathVariable podcastId: String): ResponseEntity<PodcastResponse> {
        val podcast = podcastAccess.podcast(userId, podcastId)
        return ResponseEntity.ok(podcast.toResponse())
    }

    @PutMapping("/{podcastId}")
    fun update(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @RequestBody request: UpdatePodcastRequest
    ): ResponseEntity<Any> {
        val existing = podcastAccess.podcast(userId, podcastId)
        val merged = request.applyTo(existing)
        podcastValidator.validate(merged)
        val updated = podcastService.update(podcastId, merged) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(updated.toResponse())
    }

    @DeleteMapping("/{podcastId}")
    fun delete(@PathVariable userId: String, @PathVariable podcastId: String): ResponseEntity<Void> {
        podcastAccess.podcast(userId, podcastId)
        podcastService.delete(podcastId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{podcastId}/pipeline-status")
    fun pipelineStatus(@PathVariable userId: String, @PathVariable podcastId: String): ResponseEntity<Any> {
        podcastAccess.podcast(userId, podcastId)

        val stage = episodeService.activePipelineStage(podcastId)
        return ResponseEntity.ok(mapOf("stage" to stage))
    }

    @PostMapping("/{podcastId}/generate")
    fun generate(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @RequestBody(required = false) request: GenerateEpisodeRequest?
    ): ResponseEntity<Any> {
        val podcast = podcastAccess.podcast(userId, podcastId)

        log.info("Manual briefing generation triggered for podcast {} (focus: {})", podcastId, request?.focus)

        // Generation runs in the background (it can take minutes); we return 202 with the GENERATING
        // episode id and the UI follows progress via SSE. Running it inline in the request would tie
        // it to the async-request timeout, which would cancel the in-flight pipeline.
        val episode = podcastService.generateBriefingAsync(podcast, request?.focus)
            ?: return ResponseEntity.status(409).body(mapOf("message" to "An episode is already generating for this podcast"))

        return ResponseEntity.accepted().body(mapOf("message" to "Generation started", "episodeId" to episode.id))
    }

    @PostMapping("/{podcastId}/episodes/{episodeId}/regenerate")
    fun regenerate(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable episodeId: Long,
        @RequestParam(required = false, defaultValue = "false") bypassLlmCache: Boolean
    ): ResponseEntity<Any> {
        val podcast = podcastAccess.podcast(userId, podcastId)
        val episode = podcastAccess.episode(userId, podcastId, episodeId)

        log.info("Regenerate triggered for episode {} of podcast {} (cache bypass: {})", episodeId, podcastId, bypassLlmCache)
        // Background work (recompose + TTS); return 202 with the new GENERATING episode id.
        val newEpisode = podcastService.regenerateEpisodeAsync(episode, podcast, bypassLlmCache)
        return ResponseEntity.accepted().body(mapOf("message" to "Episode regeneration started", "episodeId" to newEpisode.id))
    }

    @GetMapping("/{podcastId}/articles/{articleId}/posts")
    fun articlePosts(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable articleId: Long
    ): ResponseEntity<List<ArticlePostResponse>> {
        val podcast = podcastAccess.podcast(userId, podcastId)

        val posts = podcastService.findArticlePosts(podcast, articleId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(posts)
    }

    @GetMapping("/{podcastId}/upcoming-articles")
    fun upcomingArticles(@PathVariable userId: String, @PathVariable podcastId: String): ResponseEntity<Any> {
        val podcast = podcastAccess.podcast(userId, podcastId)

        val content = podcastService.getUpcomingContent(podcast)
        return ResponseEntity.ok(content.toResponse())
    }

    @GetMapping("/{podcastId}/preview", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun preview(@PathVariable userId: String, @PathVariable podcastId: String): Any =
        previewStreamer.stream(podcastAccess.podcast(userId, podcastId))
}
