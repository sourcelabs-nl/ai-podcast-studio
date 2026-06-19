package com.aisummarypodcast.podcast

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodeStatus
import com.aisummarypodcast.user.UserService
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.io.File

@RestController
@RequestMapping("/users/{userId}/podcasts/{podcastId}/episodes")
class EpisodeController(
    private val podcastService: PodcastService,
    private val userService: UserService,
    private val episodeService: EpisodeService,
    private val appProperties: AppProperties
) {

    private val stageCostFn = stageCostFnFromModels(appProperties.models)

    @GetMapping
    fun list(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @RequestParam(required = false) status: List<String>?,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "20") pageSize: Int
    ): ResponseEntity<Any> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        val podcast = podcastService.findById(podcastId) ?: return ResponseEntity.notFound().build()
        if (podcast.userId != userId) return ResponseEntity.notFound().build()

        if (page < 0) return ResponseEntity.badRequest().body(mapOf("error" to "page must be >= 0"))
        if (pageSize < 1 || pageSize > 200) return ResponseEntity.badRequest().body(mapOf("error" to "pageSize must be in [1, 200]"))

        val statuses = (status ?: emptyList()).map {
            try { EpisodeStatus.valueOf(it) } catch (_: IllegalArgumentException) {
                return ResponseEntity.badRequest().body(mapOf("error" to "Invalid status: $it. Valid values: ${EpisodeStatus.entries.joinToString()}"))
            }
        }

        val pageable = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "generatedAt", "id"))
        val result = episodeService.findByPodcastIdPaged(podcastId, statuses, pageable)
        return ResponseEntity.ok(result.toResponse { it.toResponse() })
    }

    @GetMapping("/{episodeId}")
    fun get(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable episodeId: Long
    ): ResponseEntity<EpisodeResponse> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        val podcast = podcastService.findById(podcastId) ?: return ResponseEntity.notFound().build()
        if (podcast.userId != userId) return ResponseEntity.notFound().build()

        val episode = episodeService.findById(episodeId)
            ?: return ResponseEntity.notFound().build()
        if (episode.podcastId != podcastId) return ResponseEntity.notFound().build()

        return ResponseEntity.ok(episode.toResponse(
            scoreCalls = episodeService.countArticles(episodeId),
            costFor = stageCostFn
        ))
    }

    @PutMapping("/{episodeId}/script")
    fun editScript(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable episodeId: Long,
        @RequestBody request: UpdateScriptRequest
    ): ResponseEntity<Any> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        val podcast = podcastService.findById(podcastId) ?: return ResponseEntity.notFound().build()
        if (podcast.userId != userId) return ResponseEntity.notFound().build()

        val episode = episodeService.findById(episodeId)
            ?: return ResponseEntity.notFound().build()
        if (episode.podcastId != podcastId) return ResponseEntity.notFound().build()

        if (episode.status != EpisodeStatus.PENDING_REVIEW) {
            return ResponseEntity.status(409).body(mapOf("error" to "Episode is not in PENDING_REVIEW status"))
        }

        val updated = episodeService.updateScript(episode, request.scriptText)
        return ResponseEntity.ok(updated.toResponse())
    }

    @PostMapping("/{episodeId}/approve")
    fun approve(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable episodeId: Long
    ): ResponseEntity<Any> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        val podcast = podcastService.findById(podcastId) ?: return ResponseEntity.notFound().build()
        if (podcast.userId != userId) return ResponseEntity.notFound().build()

        val episode = episodeService.findById(episodeId)
            ?: return ResponseEntity.notFound().build()
        if (episode.podcastId != podcastId) return ResponseEntity.notFound().build()

        if (episode.status != EpisodeStatus.PENDING_REVIEW && episode.status != EpisodeStatus.FAILED) {
            return ResponseEntity.status(409).body(mapOf("error" to "Episode is not in PENDING_REVIEW or FAILED status"))
        }

        episodeService.approveAndGenerateAudio(episode, podcast)

        return ResponseEntity.accepted().body(mapOf("message" to "Episode approved, audio generation started"))
    }

    @PostMapping("/{episodeId}/approve-publication")
    fun approvePublication(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable episodeId: Long
    ): ResponseEntity<Any> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        val podcast = podcastService.findById(podcastId) ?: return ResponseEntity.notFound().build()
        if (podcast.userId != userId) return ResponseEntity.notFound().build()

        val episode = episodeService.findById(episodeId)
            ?: return ResponseEntity.notFound().build()
        if (episode.podcastId != podcastId) return ResponseEntity.notFound().build()

        if (episode.status != EpisodeStatus.GENERATED) {
            return ResponseEntity.status(409).body(mapOf("error" to "Only GENERATED episodes can be approved for publication"))
        }

        val updated = episodeService.approveForPublication(episode, podcast)
        return ResponseEntity.ok(updated.toResponse())
    }

    @PostMapping("/{episodeId}/discard")
    fun discard(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable episodeId: Long
    ): ResponseEntity<Any> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        val podcast = podcastService.findById(podcastId) ?: return ResponseEntity.notFound().build()
        if (podcast.userId != userId) return ResponseEntity.notFound().build()

        val episode = episodeService.findById(episodeId)
            ?: return ResponseEntity.notFound().build()
        if (episode.podcastId != podcastId) return ResponseEntity.notFound().build()

        if (episode.status != EpisodeStatus.PENDING_REVIEW && episode.status != EpisodeStatus.GENERATED && episode.status != EpisodeStatus.FAILED) {
            val message = if (episode.status == EpisodeStatus.GENERATING_AUDIO) "Audio generation is in progress" else "Episode is not in a discardable status"
            return ResponseEntity.status(409).body(mapOf("error" to message))
        }

        if (episode.status == EpisodeStatus.FAILED) {
            episodeService.discardOnly(episode, podcastId)
        } else {
            episodeService.discardAndResetArticles(episode, podcastId)
        }
        return ResponseEntity.ok(mapOf("message" to "Episode discarded"))
    }

    @PostMapping("/{episodeId}/regenerate-recap")
    fun regenerateRecap(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable episodeId: Long
    ): ResponseEntity<Any> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        val podcast = podcastService.findById(podcastId) ?: return ResponseEntity.notFound().build()
        if (podcast.userId != userId) return ResponseEntity.notFound().build()

        val episode = episodeService.findById(episodeId)
            ?: return ResponseEntity.notFound().build()
        if (episode.podcastId != podcastId) return ResponseEntity.notFound().build()

        return try {
            val updated = episodeService.regenerateRecap(episode, podcast)
            ResponseEntity.ok(updated.toResponse())
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("error" to "Failed to regenerate recap: ${e.message}"))
        }
    }

    @PostMapping("/{episodeId}/retry")
    fun retry(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable episodeId: Long
    ): ResponseEntity<Any> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        val podcast = podcastService.findById(podcastId) ?: return ResponseEntity.notFound().build()
        if (podcast.userId != userId) return ResponseEntity.notFound().build()

        val episode = episodeService.findById(episodeId)
            ?: return ResponseEntity.notFound().build()
        if (episode.podcastId != podcastId) return ResponseEntity.notFound().build()

        if (episode.status != EpisodeStatus.FAILED) {
            return ResponseEntity.status(409).body(mapOf("error" to "Episode is not in FAILED status"))
        }

        val resumePoint = podcastService.retryEpisode(episode, podcast)
        return ResponseEntity.accepted().body(mapOf(
            "message" to "Retrying episode from ${resumePoint.name.lowercase().replace('_', ' ')}",
            "resumePoint" to resumePoint.name
        ))
    }

    @PostMapping("/{episodeId}/regenerate-audio")
    fun regenerateAudio(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable episodeId: Long
    ): ResponseEntity<Any> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        val podcast = podcastService.findById(podcastId) ?: return ResponseEntity.notFound().build()
        if (podcast.userId != userId) return ResponseEntity.notFound().build()

        val episode = episodeService.findById(episodeId)
            ?: return ResponseEntity.notFound().build()
        if (episode.podcastId != podcastId) return ResponseEntity.notFound().build()

        if (episode.status != EpisodeStatus.GENERATED) {
            return ResponseEntity.status(409).body(mapOf("error" to "Episode is not in GENERATED status"))
        }

        episodeService.regenerateAudio(episode, podcast)
        return ResponseEntity.accepted().body(mapOf("message" to "Audio regeneration started"))
    }

    @GetMapping("/{episodeId}/audio")
    fun streamAudio(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable episodeId: Long
    ): ResponseEntity<Resource> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        val podcast = podcastService.findById(podcastId) ?: return ResponseEntity.notFound().build()
        if (podcast.userId != userId) return ResponseEntity.notFound().build()

        val episode = episodeService.findById(episodeId)
            ?: return ResponseEntity.notFound().build()
        if (episode.podcastId != podcastId) return ResponseEntity.notFound().build()

        val audioPath = episode.audioFilePath ?: return ResponseEntity.notFound().build()
        val file = File(audioPath)
        if (!file.exists()) return ResponseEntity.notFound().build()

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("audio/mpeg"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"${file.name}\"")
            .header(HttpHeaders.ACCEPT_RANGES, "bytes")
            .body(FileSystemResource(file))
    }

    @GetMapping("/{episodeId}/articles")
    fun articles(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable episodeId: Long
    ): ResponseEntity<List<EpisodeArticleResponse>> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        val podcast = podcastService.findById(podcastId) ?: return ResponseEntity.notFound().build()
        if (podcast.userId != userId) return ResponseEntity.notFound().build()

        val episode = episodeService.findById(episodeId)
            ?: return ResponseEntity.notFound().build()
        if (episode.podcastId != podcastId) return ResponseEntity.notFound().build()

        return ResponseEntity.ok(episodeService.findArticlesForEpisode(episodeId))
    }

}
