package com.aisummarypodcast.eval

import com.aisummarypodcast.podcast.EpisodeService
import com.aisummarypodcast.podcast.PodcastService
import com.aisummarypodcast.store.EpisodeScore
import com.aisummarypodcast.user.UserService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Triggers scoring and reads scores back over HTTP, so a score is never obtained by querying the
 * database directly.
 */
@RestController
@RequestMapping("/users/{userId}/podcasts/{podcastId}")
class EpisodeScoringController(
    private val userService: UserService,
    private val podcastService: PodcastService,
    private val episodeService: EpisodeService,
    private val episodeScoringService: EpisodeScoringService
) {

    private companion object {
        const val MAX_EPISODES = 500
    }

    @PostMapping("/scores")
    suspend fun scorePodcast(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @RequestParam(required = false, defaultValue = "50") limit: Int
    ): ResponseEntity<Any> {
        if (!ownsPodcast(userId, podcastId)) return ResponseEntity.notFound().build()
        if (limit < 1 || limit > MAX_EPISODES) {
            return ResponseEntity.badRequest().body(mapOf("error" to "limit must be in [1, $MAX_EPISODES]"))
        }
        return ResponseEntity.ok(episodeScoringService.scorePodcast(podcastId, limit))
    }

    @PostMapping("/episodes/{episodeId}/scores")
    suspend fun scoreEpisode(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable episodeId: Long
    ): ResponseEntity<ScoringOutcome> {
        val episode = ownedEpisode(userId, podcastId, episodeId) ?: return ResponseEntity.notFound().build()
        val outcome = episodeScoringService.scoreEpisode(episode) ?: return ResponseEntity.noContent().build()
        return ResponseEntity.ok(outcome)
    }

    @GetMapping("/episodes/{episodeId}/scores")
    fun readScores(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable episodeId: Long
    ): ResponseEntity<List<EpisodeScore>> {
        ownedEpisode(userId, podcastId, episodeId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(episodeScoringService.existingScores(episodeId))
    }

    private fun ownedEpisode(userId: String, podcastId: String, episodeId: Long) =
        if (!ownsPodcast(userId, podcastId)) null
        else episodeService.findById(episodeId)?.takeIf { it.podcastId == podcastId }

    private fun ownsPodcast(userId: String, podcastId: String): Boolean {
        userService.findById(userId) ?: return false
        val podcast = podcastService.findById(podcastId) ?: return false
        return podcast.userId == userId
    }
}
