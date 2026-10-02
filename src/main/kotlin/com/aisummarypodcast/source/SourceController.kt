package com.aisummarypodcast.source

import com.aisummarypodcast.podcast.PodcastAccess
import com.aisummarypodcast.store.SourceType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.URI

@RestController
@RequestMapping("/users/{userId}/podcasts/{podcastId}/sources")
class SourceController(
    private val sourceService: SourceService,
    private val podcastAccess: PodcastAccess
) {

    @PostMapping
    fun create(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @RequestBody request: CreateSourceRequest
    ): ResponseEntity<Any> {
        podcastAccess.podcast(userId, podcastId)

        val sourceType = SourceType.fromValue(request.type)
            ?: return ResponseEntity.badRequest().build()
        val source = sourceService.create(podcastId, sourceType, request.url, request.toConfig())
        return ResponseEntity.created(URI.create("/users/$userId/podcasts/$podcastId/sources/${source.id}"))
            .body(source.toResponse())
    }

    @GetMapping
    fun list(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @RequestParam(required = false) enabled: Boolean?
    ): ResponseEntity<List<SourceResponse>> {
        val podcast = podcastAccess.podcast(userId, podcastId)

        val sources = sourceService.findByPodcastId(podcastId, enabled)
        val sourceIds = sources.map { it.id }
        val articleCounts = sourceService.getArticleCounts(sourceIds, podcast.relevanceThreshold)
        val postCounts = sourceService.getPostCounts(sourceIds)
        val breakerStates = sourceService.getHostBreakerStates(sources)

        return ResponseEntity.ok(sources.map { source ->
            val c = articleCounts[source.id]
            source.toResponse().copy(
                articleCount = c?.total ?: 0,
                relevantArticleCount = c?.relevant ?: 0,
                postCount = postCounts[source.id] ?: 0
            ).withBreakerState(breakerStates[source.id])
        })
    }

    @PutMapping("/{sourceId}")
    fun update(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable sourceId: String,
        @RequestBody request: UpdateSourceRequest
    ): ResponseEntity<SourceResponse> {
        podcastAccess.podcast(userId, podcastId)

        val source = sourceService.findById(sourceId) ?: return ResponseEntity.notFound().build()
        if (source.podcastId != podcastId) return ResponseEntity.notFound().build()

        val sourceType = SourceType.fromValue(request.type)
            ?: return ResponseEntity.badRequest().build()
        val updated = sourceService.update(sourceId, sourceType, request.url, request.toConfig())
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(updated.toResponse())
    }

    @DeleteMapping("/{sourceId}")
    fun delete(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable sourceId: String
    ): ResponseEntity<Void> {
        podcastAccess.podcast(userId, podcastId)

        val source = sourceService.findById(sourceId) ?: return ResponseEntity.notFound().build()
        if (source.podcastId != podcastId) return ResponseEntity.notFound().build()

        sourceService.delete(sourceId)
        return ResponseEntity.noContent().build()
    }

}
