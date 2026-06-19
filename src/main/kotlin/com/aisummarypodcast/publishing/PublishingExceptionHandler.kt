package com.aisummarypodcast.publishing

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.client.HttpClientErrorException

/**
 * Translates publishing failures to HTTP responses, keeping that web concern out of the controller
 * (Rule SB8). Scoped to [PublishingController] so the mapping does not leak to unrelated controllers.
 * The `unpublish` endpoint handles its own exceptions inline (it maps [IllegalStateException] to 404),
 * so those never reach this advice.
 */
@RestControllerAdvice(assignableTypes = [PublishingController::class])
class PublishingExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(SoundCloudQuotaExceededException::class)
    fun handleQuotaExceeded(e: SoundCloudQuotaExceededException): ResponseEntity<Any> {
        log.warn("SoundCloud upload quota full: {}", e.message)
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
            mapOf(
                "error" to e.message,
                "code" to "quota_exceeded",
                "secondsToFree" to e.plan.secondsToFree,
                "tracksToDelete" to e.plan.tracksToDelete.map {
                    mapOf(
                        "id" to it.id,
                        "title" to it.title,
                        "createdAt" to it.createdAt,
                        "durationSeconds" to it.durationSeconds
                    )
                }
            )
        )
    }

    @ExceptionHandler(UnsupportedOperationException::class)
    fun handleUnsupported(e: UnsupportedOperationException): ResponseEntity<Any> =
        ResponseEntity.badRequest().body(mapOf("error" to e.message))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(e: IllegalArgumentException): ResponseEntity<Any> =
        ResponseEntity.badRequest().body(mapOf("error" to e.message))

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(e: IllegalStateException): ResponseEntity<Any> {
        val message = e.message ?: "Publishing failed"
        return when {
            message.contains("not configured or enabled", ignoreCase = true) ->
                ResponseEntity.badRequest().body(mapOf("error" to message, "code" to "target_not_configured"))
            message.contains("re-authorize", ignoreCase = true) || message.contains("refresh failed", ignoreCase = true) ->
                oauthExpired(message)
            message.contains("already published") ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to message))
            message.contains("approved for publication", ignoreCase = true) ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to message, "code" to "approval_required"))
            else ->
                ResponseEntity.badRequest().body(mapOf("error" to message))
        }
    }

    @ExceptionHandler(HttpClientErrorException.Unauthorized::class)
    fun handleUnauthorized(e: HttpClientErrorException.Unauthorized): ResponseEntity<Any> = oauthExpired(e.message)

    @ExceptionHandler(Exception::class)
    fun handleGeneric(e: Exception): ResponseEntity<Any> {
        log.error("Publishing failed: {}", e.message, e)
        return ResponseEntity.internalServerError().body(mapOf("error" to "Publishing failed: ${e.message}"))
    }

    private fun oauthExpired(detail: String?): ResponseEntity<Any> {
        log.error("SoundCloud authorization failed: {}", detail)
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            mapOf(
                "error" to "SoundCloud authorization expired. Please re-authorize your account.",
                "code" to "oauth_expired"
            )
        )
    }
}
