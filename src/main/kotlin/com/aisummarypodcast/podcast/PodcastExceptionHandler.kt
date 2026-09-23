package com.aisummarypodcast.podcast

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Translates typed podcast failures to HTTP responses, keeping that web concern out of the
 * controller (Rule SB8). Scoped to [PodcastController], [EpisodeController] and
 * [ExperimentController], and deliberately handles only the specific types below: no catch-all
 * handler, so every other error path keeps the behaviour it has today.
 */
@RestControllerAdvice(assignableTypes = [PodcastController::class, EpisodeController::class, ExperimentController::class])
class PodcastExceptionHandler {

    @ExceptionHandler(EpisodeNotRegenerableException::class)
    fun handleNotRegenerable(e: EpisodeNotRegenerableException): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(mapOf("error" to e.message, "code" to "episode_not_regenerable"))

    @ExceptionHandler(EpisodeNotRerunnableException::class)
    fun handleNotRerunnable(e: EpisodeNotRerunnableException): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(mapOf("error" to e.message, "code" to "episode_not_rerunnable"))

    @ExceptionHandler(EpisodeNotRecomposableException::class)
    fun handleNotRecomposable(e: EpisodeNotRecomposableException): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(mapOf("error" to e.message, "code" to "episode_not_recomposable"))

    @ExceptionHandler(EpisodePublishedException::class)
    fun handlePublished(e: EpisodePublishedException): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(mapOf("error" to e.message, "code" to "episode_published"))

    @ExceptionHandler(InvalidExperimentException::class)
    fun handleInvalidExperiment(e: InvalidExperimentException): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(mapOf("error" to e.message, "code" to "invalid_experiment"))

    @ExceptionHandler(ExperimentTooLargeException::class)
    fun handleExperimentTooLarge(e: ExperimentTooLargeException): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(mapOf("error" to e.message, "code" to "experiment_too_large"))

    @ExceptionHandler(EpisodeNotExperimentableException::class)
    fun handleNotExperimentable(e: EpisodeNotExperimentableException): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(mapOf("error" to e.message, "code" to "episode_not_experimentable"))
}
