package com.aisummarypodcast.publishing

import com.aisummarypodcast.podcast.PodcastService
import com.aisummarypodcast.user.UserService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/users/{userId}/podcasts/{podcastId}/playlist")
class PlaylistController(
    private val userService: UserService,
    private val podcastService: PodcastService,
    private val publishingService: PublishingService
) {

    @PostMapping("/rebuild")
    fun rebuildPlaylist(
        @PathVariable userId: String,
        @PathVariable podcastId: String
    ): ResponseEntity<Any> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        val podcast = podcastService.findById(podcastId) ?: return ResponseEntity.notFound().build()
        if (podcast.userId != userId) return ResponseEntity.notFound().build()

        val trackIds = publishingService.rebuildSoundCloudPlaylist(podcast, userId)
        return ResponseEntity.ok(mapOf("trackIds" to trackIds))
    }
}
