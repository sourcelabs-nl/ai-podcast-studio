package com.aisummarypodcast.source

import com.aisummarypodcast.podcast.PodcastService
import com.aisummarypodcast.store.SourceType
import com.aisummarypodcast.user.UserService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.URI

@RestController
@RequestMapping("/users/{userId}/podcasts/{podcastId}/sources")
class SourceController(
    private val sourceService: SourceService,
    private val podcastService: PodcastService,
    private val userService: UserService
) {

    @PostMapping
    fun create(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @RequestBody request: CreateSourceRequest
    ): ResponseEntity<Any> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        val podcast = podcastService.findById(podcastId) ?: return ResponseEntity.notFound().build()
        if (podcast.userId != userId) return ResponseEntity.notFound().build()

        val sourceType = SourceType.fromValue(request.type)
            ?: return ResponseEntity.badRequest().build()
        return try {
            val source = sourceService.create(podcastId, sourceType, request.url, request.toConfig())
            ResponseEntity.created(URI.create("/users/$userId/podcasts/$podcastId/sources/${source.id}"))
                .body(source.toResponse())
        } catch (e: IllegalArgumentException) {
            ResponseEntity.unprocessableEntity().body(mapOf("error" to e.message))
        }
    }

    @GetMapping
    fun list(@PathVariable userId: String, @PathVariable podcastId: String): ResponseEntity<List<SourceResponse>> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        val podcast = podcastService.findById(podcastId) ?: return ResponseEntity.notFound().build()
        if (podcast.userId != userId) return ResponseEntity.notFound().build()

        val sources = sourceService.findByPodcastId(podcastId)
        val sourceIds = sources.map { it.id }
        val articleCounts = sourceService.getArticleCounts(sourceIds, podcast.relevanceThreshold)
        val postCounts = sourceService.getPostCounts(sourceIds)

        return ResponseEntity.ok(sources.map { source ->
            val c = articleCounts[source.id]
            source.toResponse().copy(
                articleCount = c?.total ?: 0,
                relevantArticleCount = c?.relevant ?: 0,
                postCount = postCounts[source.id] ?: 0
            )
        })
    }

    @PutMapping("/{sourceId}")
    fun update(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable sourceId: String,
        @RequestBody request: UpdateSourceRequest
    ): ResponseEntity<SourceResponse> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        val podcast = podcastService.findById(podcastId) ?: return ResponseEntity.notFound().build()
        if (podcast.userId != userId) return ResponseEntity.notFound().build()

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
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        val podcast = podcastService.findById(podcastId) ?: return ResponseEntity.notFound().build()
        if (podcast.userId != userId) return ResponseEntity.notFound().build()

        val source = sourceService.findById(sourceId) ?: return ResponseEntity.notFound().build()
        if (source.podcastId != podcastId) return ResponseEntity.notFound().build()

        sourceService.delete(sourceId)
        return ResponseEntity.noContent().build()
    }

}
