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
 * Mapping is by exception type (see [PublishingExceptions]); no exception-message sniffing.
 */
@RestControllerAdvice(assignableTypes = [PublishingController::class])
class PublishingExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(UnsupportedOperationException::class)
    fun handleUnsupported(e: UnsupportedOperationException): ResponseEntity<Any> =
        ResponseEntity.badRequest().body(mapOf("error" to e.message))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(e: IllegalArgumentException): ResponseEntity<Any> =
        ResponseEntity.badRequest().body(mapOf("error" to e.message))

    @ExceptionHandler(TargetNotConfiguredException::class)
    fun handleTargetNotConfigured(e: TargetNotConfiguredException): ResponseEntity<Any> =
        ResponseEntity.badRequest().body(mapOf("error" to e.message, "code" to "target_not_configured"))

    @ExceptionHandler(PublishApprovalRequiredException::class)
    fun handleApprovalRequired(e: PublishApprovalRequiredException): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to e.message, "code" to "approval_required"))

    @ExceptionHandler(AlreadyPublishedException::class)
    fun handleAlreadyPublished(e: AlreadyPublishedException): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to e.message))

    @ExceptionHandler(NoPublicationFoundException::class)
    fun handlePublicationNotFound(e: NoPublicationFoundException): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to e.message))

    @ExceptionHandler(OAuthExpiredException::class)
    fun handleOAuthExpired(e: OAuthExpiredException): ResponseEntity<Any> = oauthExpired(e.message)

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(e: IllegalStateException): ResponseEntity<Any> =
        ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Publishing failed")))

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
