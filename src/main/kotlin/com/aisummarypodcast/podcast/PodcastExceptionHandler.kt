package com.aisummarypodcast.podcast

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Translates typed podcast failures to HTTP responses, keeping that web concern out of the
 * controller (Rule SB8). Scoped to [PodcastController], and deliberately handles only the specific
 * types below: no catch-all handler, so every other error path keeps the behaviour it has today.
 */
@RestControllerAdvice(assignableTypes = [PodcastController::class])
class PodcastExceptionHandler {

    @ExceptionHandler(EpisodeNotRegenerableException::class)
    fun handleNotRegenerable(e: EpisodeNotRegenerableException): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(mapOf("error" to e.message, "code" to "episode_not_regenerable"))
}
