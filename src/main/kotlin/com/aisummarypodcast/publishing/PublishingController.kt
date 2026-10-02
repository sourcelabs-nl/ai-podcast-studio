package com.aisummarypodcast.publishing

import com.aisummarypodcast.podcast.PodcastAccess
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/users/{userId}/podcasts/{podcastId}/episodes/{episodeId}")
class PublishingController(
    private val podcastAccess: PodcastAccess,
    private val publishingService: PublishingService
) {

    @PostMapping("/publish/{target}")
    suspend fun publish(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable episodeId: Long,
        @PathVariable target: String
    ): ResponseEntity<Any> {
        val podcast = podcastAccess.podcast(userId, podcastId)
        val episode = podcastAccess.episode(userId, podcastId, episodeId)

        // SoundCloud quota is freed automatically inside the publisher; remaining publishing
        // failures are translated to HTTP by PublishingExceptionHandler (Rule SB8).
        val publication = publishingService.publish(episode, podcast, userId, target)
        return ResponseEntity.ok(publication.toResponse())
    }

    @DeleteMapping("/publications/{target}")
    fun unpublish(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable episodeId: Long,
        @PathVariable target: String
    ): ResponseEntity<Any> {
        val podcast = podcastAccess.podcast(userId, podcastId)
        val episode = podcastAccess.episode(userId, podcastId, episodeId)

        // Publishing failures are translated to HTTP by PublishingExceptionHandler (Rule SB8).
        val publication = publishingService.unpublish(episode, podcast, userId, target)
        return ResponseEntity.ok(publication.toResponse())
    }

    @GetMapping("/publications")
    fun listPublications(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable episodeId: Long
    ): ResponseEntity<Any> {
        podcastAccess.episode(userId, podcastId, episodeId)

        val publications = publishingService.getPublications(episodeId)
        return ResponseEntity.ok(publications.map { it.toResponse() })
    }

}
