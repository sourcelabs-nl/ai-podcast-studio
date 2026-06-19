package com.aisummarypodcast.publishing

import com.aisummarypodcast.podcast.EpisodeService
import com.aisummarypodcast.podcast.PodcastService
import com.aisummarypodcast.user.UserService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.client.HttpClientErrorException
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
    fun publish(
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
            val publication = publishingService.publish(episode, podcast, userId, target)
            ResponseEntity.ok(publication.toResponse())
        } catch (e: SoundCloudQuotaExceededException) {
            log.warn("SoundCloud quota exceeded for episode {} to {}: {}", episodeId, target, e.message)
            ResponseEntity.status(413).body(mapOf(
                "error" to e.message,
                "code" to "quota_exceeded",
                "oldestTrack" to e.oldestTrack?.let {
                    mapOf(
                        "id" to it.id,
                        "title" to it.title,
                        "createdAt" to it.createdAt,
                        "duration" to it.duration
                    )
                }
            ))
        } catch (e: UnsupportedOperationException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        } catch (e: IllegalStateException) {
            val message = e.message ?: "Publishing failed"
            if (message.contains("not configured or enabled", ignoreCase = true)) {
                ResponseEntity.badRequest().body(mapOf("error" to message, "code" to "target_not_configured"))
            } else if (message.contains("re-authorize", ignoreCase = true) || message.contains("refresh failed", ignoreCase = true)) {
                log.error("OAuth authorization failed for episode {} to {}: {}", episodeId, target, e.message, e)
                ResponseEntity.status(401).body(mapOf(
                    "error" to "SoundCloud authorization expired. Please re-authorize your account.",
                    "code" to "oauth_expired"
                ))
            } else if (message.contains("already published")) {
                ResponseEntity.status(409).body(mapOf("error" to message))
            } else if (message.contains("approved for publication", ignoreCase = true)) {
                ResponseEntity.status(409).body(mapOf("error" to message, "code" to "approval_required"))
            } else {
                ResponseEntity.badRequest().body(mapOf("error" to message))
            }
        } catch (e: HttpClientErrorException.Unauthorized) {
            log.error("OAuth authorization failed for episode {} to {}: {}", episodeId, target, e.message, e)
            ResponseEntity.status(401).body(mapOf(
                "error" to "SoundCloud authorization expired. Please re-authorize your account.",
                "code" to "oauth_expired"
            ))
        } catch (e: Exception) {
            log.error("Publishing failed for episode {} to {}: {}", episodeId, target, e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to "Publishing failed: ${e.message}"))
        }
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
