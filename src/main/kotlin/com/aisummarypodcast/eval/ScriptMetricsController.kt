package com.aisummarypodcast.eval

import com.aisummarypodcast.podcast.PodcastAccess
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
    private val podcastAccess: PodcastAccess,
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
        val episode = podcastAccess.episode(userId, podcastId, episodeId)
        return ResponseEntity.ok(scriptMetricsService.forEpisode(episode))
    }

    @GetMapping("/metrics")
    fun forPodcast(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @RequestParam(required = false, defaultValue = "50") limit: Int
    ): ResponseEntity<Any> {
        podcastAccess.podcast(userId, podcastId)
        if (limit < 1 || limit > MAX_EPISODES) {
            return ResponseEntity.badRequest().body(mapOf("error" to "limit must be in [1, $MAX_EPISODES]"))
        }
        return ResponseEntity.ok(scriptMetricsService.forPodcast(podcastId, limit))
    }
}
