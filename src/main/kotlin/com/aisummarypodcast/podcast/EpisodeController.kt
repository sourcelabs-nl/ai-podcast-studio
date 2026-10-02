package com.aisummarypodcast.podcast

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodeStatus
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
    private val podcastAccess: PodcastAccess,
    private val episodeService: EpisodeService,
    private val episodeSearchService: EpisodeSearchService,
    private val episodeDiscardService: EpisodeDiscardService,
    private val appProperties: AppProperties
) {

    private val stageCostFn = stageCostFnFromModels(appProperties.models)

    @GetMapping
    fun list(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @RequestParam(required = false) status: List<String>?,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "20") pageSize: Int
    ): ResponseEntity<Any> {
        podcastAccess.podcast(userId, podcastId)

        if (page < 0) return ResponseEntity.badRequest().body(mapOf("error" to "page must be >= 0"))
        if (pageSize < 1 || pageSize > 200) return ResponseEntity.badRequest().body(mapOf("error" to "pageSize must be in [1, 200]"))

        val statuses = (status ?: emptyList()).map {
            try { EpisodeStatus.valueOf(it) } catch (_: IllegalArgumentException) {
                return ResponseEntity.badRequest().body(mapOf("error" to "Invalid status: $it. Valid values: ${EpisodeStatus.entries.joinToString()}"))
            }
        }

        val pageable = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "generatedAt", "id"))
        // A query too short to search falls through to the plain listing rather than erroring,
        // so the list stays usable while the user is still typing.
        if (EpisodeSearchService.isSearchable(q)) {
            val hits = episodeSearchService.search(podcastId, statuses, q!!, pageable)
            return ResponseEntity.ok(hits.toResponse { it.toResponse() })
        }
        val result = episodeService.findByPodcastIdPaged(podcastId, statuses, pageable)
        return ResponseEntity.ok(result.toResponse { it.toResponse() })
    }

    @GetMapping("/{episodeId}")
    fun get(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable episodeId: Long
    ): ResponseEntity<EpisodeResponse> {
        val episode = podcastAccess.episode(userId, podcastId, episodeId)

        return ResponseEntity.ok(episode.toResponse(
            EpisodeCostContext(
                scoreStage = episodeService.scoreStageSummary(episodeId),
                costFor = stageCostFn,
                // The gate runs on a configured model rather than one resolved per podcast, so the
                // costs view has nowhere else to read its name from.
                dedupGateModel = appProperties.llm.dedup.gate.model,
                projection = episodeService.costProjection(episodeId)
            )
        ))
    }

    @PutMapping("/{episodeId}/script")
    fun editScript(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable episodeId: Long,
        @RequestBody request: UpdateScriptRequest
    ): ResponseEntity<Any> {
        val episode = podcastAccess.episode(userId, podcastId, episodeId)

        val updated = episodeService.updateScript(episode, request.scriptText)
        return ResponseEntity.ok(updated.toResponse())
    }

    /**
     * Recomposes a focus episode under review with the reviewer's feedback, against the same
     * articles, in the background. The same episode is updated and stays in review.
     */
    @PostMapping("/{episodeId}/regenerate-script")
    fun regenerateScript(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable episodeId: Long,
        @RequestBody request: RegenerateScriptRequest
    ): ResponseEntity<Any> {
        val podcast = podcastAccess.podcast(userId, podcastId)
        val episode = podcastAccess.episode(userId, podcastId, episodeId)

        if (request.feedback.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "feedback must not be blank"))
        }

        val updated = podcastService.recomposeFocusEpisodeAsync(episode, podcast, request.feedback.trim())
        return ResponseEntity.accepted().body(updated.toResponse())
    }

    @GetMapping("/{episodeId}/research-sources")
    fun researchSources(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable episodeId: Long
    ): ResponseEntity<List<ResearchSourceResponse>> {
        podcastAccess.episode(userId, podcastId, episodeId)

        return ResponseEntity.ok(episodeService.findResearchSources(episodeId).map { it.toResponse() })
    }

    @PostMapping("/{episodeId}/approve")
    fun approve(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable episodeId: Long
    ): ResponseEntity<Any> {
        val podcast = podcastAccess.podcast(userId, podcastId)
        val episode = podcastAccess.episode(userId, podcastId, episodeId)

        episodeService.approveAndGenerateAudio(episode, podcast)

        return ResponseEntity.accepted().body(mapOf("message" to "Episode approved, audio generation started"))
    }

    @PostMapping("/{episodeId}/approve-publication")
    fun approvePublication(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable episodeId: Long
    ): ResponseEntity<Any> {
        val podcast = podcastAccess.podcast(userId, podcastId)
        val episode = podcastAccess.episode(userId, podcastId, episodeId)

        val updated = episodeService.approveForPublication(episode, podcast)
        return ResponseEntity.ok(updated.toResponse())
    }

    @PostMapping("/{episodeId}/discard")
    fun discard(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable episodeId: Long
    ): ResponseEntity<Any> {
        val episode = podcastAccess.episode(userId, podcastId, episodeId)

        episodeDiscardService.discard(episode, podcastId)
        return ResponseEntity.ok(mapOf("message" to "Episode discarded"))
    }

    @PostMapping("/{episodeId}/regenerate-recap")
    suspend fun regenerateRecap(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable episodeId: Long
    ): ResponseEntity<Any> {
        val podcast = podcastAccess.podcast(userId, podcastId)
        val episode = podcastAccess.episode(userId, podcastId, episodeId)

        val updated = episodeService.regenerateRecap(episode, podcast)
        return ResponseEntity.ok(updated.toResponse())
    }

    @PostMapping("/{episodeId}/retry")
    fun retry(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable episodeId: Long
    ): ResponseEntity<Any> {
        val podcast = podcastAccess.podcast(userId, podcastId)
        val episode = podcastAccess.episode(userId, podcastId, episodeId)

        val resumePoint = podcastService.retryEpisode(episode, podcast)
        return ResponseEntity.accepted().body(mapOf(
            "message" to "Retrying episode from ${resumePoint.name.lowercase().replace('_', ' ')}",
            "resumePoint" to resumePoint.name
        ))
    }

    /**
     * Re-runs the article window of a failed or discarded episode as a fresh episode, so a past day
     * can be reproduced. The new episode is returned immediately while the pipeline runs in the
     * background.
     */
    @PostMapping("/{episodeId}/rerun")
    fun rerun(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable episodeId: Long
    ): ResponseEntity<Any> {
        val podcast = podcastAccess.podcast(userId, podcastId)
        val episode = podcastAccess.episode(userId, podcastId, episodeId)

        val rerun = podcastService.rerunEpisodeAsync(episode, podcast)
        return ResponseEntity.accepted().body(rerun.toResponse())
    }

    @PostMapping("/{episodeId}/regenerate-audio")
    fun regenerateAudio(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable episodeId: Long
    ): ResponseEntity<Any> {
        val podcast = podcastAccess.podcast(userId, podcastId)
        val episode = podcastAccess.episode(userId, podcastId, episodeId)

        episodeService.regenerateAudio(episode, podcast)
        return ResponseEntity.accepted().body(mapOf("message" to "Audio regeneration started"))
    }

    @GetMapping("/{episodeId}/audio")
    fun streamAudio(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable episodeId: Long
    ): ResponseEntity<Resource> {
        val episode = podcastAccess.episode(userId, podcastId, episodeId)

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
        podcastAccess.episode(userId, podcastId, episodeId)

        return ResponseEntity.ok(episodeService.findArticlesForEpisode(episodeId))
    }

}
