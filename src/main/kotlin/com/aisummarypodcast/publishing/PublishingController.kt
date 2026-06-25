package com.aisummarypodcast.publishing

import com.aisummarypodcast.podcast.EpisodeService
import com.aisummarypodcast.podcast.PodcastService
import com.aisummarypodcast.user.UserService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/users/{userId}/podcasts/{podcastId}/episodes/{episodeId}")
class PublishingController(
    private val userService: UserService,
    private val podcastService: PodcastService,
    private val episodeService: EpisodeService,
    private val publishingService: PublishingService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/publish/{target}")
    suspend fun publish(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable episodeId: Long,
        @PathVariable target: String
    ): ResponseEntity<Any> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        val podcast = podcastService.findById(podcastId) ?: return ResponseEntity.notFound().build()
        if (podcast.userId != userId) return ResponseEntity.notFound().build()

        val episode = episodeService.findById(episodeId)
            ?: return ResponseEntity.notFound().build()
        if (episode.podcastId != podcastId) return ResponseEntity.notFound().build()

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
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        val podcast = podcastService.findById(podcastId) ?: return ResponseEntity.notFound().build()
        if (podcast.userId != userId) return ResponseEntity.notFound().build()

        val episode = episodeService.findById(episodeId)
            ?: return ResponseEntity.notFound().build()
        if (episode.podcastId != podcastId) return ResponseEntity.notFound().build()

        return try {
            val publication = publishingService.unpublish(episode, podcast, userId, target)
            ResponseEntity.ok(publication.toResponse())
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        } catch (e: IllegalStateException) {
            ResponseEntity.notFound().build()
        } catch (e: Exception) {
            log.error("Unpublish failed for episode {} from {}: {}", episodeId, target, e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to "Unpublish failed: ${e.message}"))
        }
    }

    @GetMapping("/publications")
    fun listPublications(
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

        val publications = publishingService.getPublications(episodeId)
        return ResponseEntity.ok(publications.map { it.toResponse() })
    }

}
