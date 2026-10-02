package com.aisummarypodcast.publishing

import com.aisummarypodcast.podcast.PodcastAccess
import tools.jackson.databind.ObjectMapper
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/users/{userId}/podcasts/{podcastId}/publication-targets")
class PodcastPublicationTargetController(
    private val podcastAccess: PodcastAccess,
    private val targetService: PodcastPublicationTargetService,
    private val objectMapper: ObjectMapper
) {

    @GetMapping
    fun list(
        @PathVariable userId: String,
        @PathVariable podcastId: String
    ): ResponseEntity<Any> {
        podcastAccess.podcast(userId, podcastId)

        val targets = targetService.list(podcastId).map { it.toResponse(objectMapper) }
        return ResponseEntity.ok(targets)
    }

    @PutMapping("/{target}")
    fun upsert(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable target: String,
        @RequestBody request: PublicationTargetRequest
    ): ResponseEntity<Any> {
        podcastAccess.podcast(userId, podcastId)

        val configJson = objectMapper.writeValueAsString(request.config ?: emptyMap<String, Any>())
        val saved = targetService.upsert(podcastId, target, configJson, request.enabled, request.autoPublish)
        return ResponseEntity.ok(saved.toResponse(objectMapper))
    }

    @DeleteMapping("/{target}")
    fun delete(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable target: String
    ): ResponseEntity<Any> {
        podcastAccess.podcast(userId, podcastId)

        return if (targetService.delete(podcastId, target)) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.notFound().build()
        }
    }

}
