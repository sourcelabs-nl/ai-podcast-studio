package com.aisummarypodcast.podcast

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Experiments on an episode's article set: start one (202, the runs execute in the background) and
 * compare every experiment run against the episode so far.
 */
@RestController
@RequestMapping("/users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/experiments")
class ExperimentController(
    private val podcastAccess: PodcastAccess,
    private val experimentService: ExperimentService
) {

    @PostMapping
    fun start(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable episodeId: Long,
        @RequestBody request: ExperimentRequest
    ): ResponseEntity<Any> {
        val podcast = podcastAccess.podcast(userId, podcastId)
        val episode = podcastAccess.episode(userId, podcastId, episodeId)
        val started = experimentService.startExperiment(episode, podcast, request.toPlan())
        return ResponseEntity.accepted().body(started.toResponse())
    }

    @GetMapping
    fun compare(
        @PathVariable userId: String,
        @PathVariable podcastId: String,
        @PathVariable episodeId: Long
    ): ResponseEntity<Any> {
        val episode = podcastAccess.episode(userId, podcastId, episodeId)
        return ResponseEntity.ok(experimentService.compare(episode))
    }
}
