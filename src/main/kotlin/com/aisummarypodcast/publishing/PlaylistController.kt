package com.aisummarypodcast.publishing

import com.aisummarypodcast.podcast.PodcastAccess
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/users/{userId}/podcasts/{podcastId}/playlist")
class PlaylistController(
    private val podcastAccess: PodcastAccess,
    private val publishingService: PublishingService
) {

    @PostMapping("/rebuild")
    fun rebuildPlaylist(
        @PathVariable userId: String,
        @PathVariable podcastId: String
    ): ResponseEntity<Any> {
        val podcast = podcastAccess.podcast(userId, podcastId)

        val trackIds = publishingService.rebuildSoundCloudPlaylist(podcast, userId)
        return ResponseEntity.ok(mapOf("trackIds" to trackIds))
    }
}
