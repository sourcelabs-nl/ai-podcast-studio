package com.aisummarypodcast.eval

import com.aisummarypodcast.podcast.EpisodeService
import com.aisummarypodcast.podcast.PodcastService
import com.aisummarypodcast.user.UserService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Reads structural metrics over HTTP so the archive is never inspected by querying the database
 * directly.
 */
@RestController
@RequestMapping("/users/{userId}/podcasts/{podcastId}")
class ScriptMetricsController(
    private val userService: UserService,
    private val podcastService: PodcastService,
    private val episodeService: EpisodeService,
    private val scriptMetricsService: ScriptMetricsService
) {

    private companion object {
        const val MAX_EPISODES = 500
    }

    @GetMapping("/episodes/{episodeId}/metrics")
    fun forEpisode(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable episodeId: Long
    ): ResponseEntity<EpisodeScriptMetrics> {
        if (!ownsPodcast(userId, podcastId)) return ResponseEntity.notFound().build()
        val episode = episodeService.findById(episodeId) ?: return ResponseEntity.notFound().build()
        if (episode.podcastId != podcastId) return ResponseEntity.notFound().build()
        return ResponseEntity.ok(scriptMetricsService.forEpisode(episode))
    }

    @GetMapping("/metrics")
    fun forPodcast(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @RequestParam(required = false, defaultValue = "50") limit: Int
    ): ResponseEntity<Any> {
        if (!ownsPodcast(userId, podcastId)) return ResponseEntity.notFound().build()
        if (limit < 1 || limit > MAX_EPISODES) {
            return ResponseEntity.badRequest().body(mapOf("error" to "limit must be in [1, $MAX_EPISODES]"))
        }
        return ResponseEntity.ok(scriptMetricsService.forPodcast(podcastId, limit))
    }

    private fun ownsPodcast(userId: String, podcastId: String): Boolean {
        userService.findById(userId) ?: return false
        val podcast = podcastService.findById(podcastId) ?: return false
        return podcast.userId == userId
    }
}
