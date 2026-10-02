package com.aisummarypodcast.api

import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Maps the typed API exceptions to `{"error": message}` responses, keeping that web concern out of
 * the controllers (Rule SB8). It has the lowest precedence so the scoped handlers keep priority,
 * and it handles only these types: no catch-all, so every other error keeps Spring's default.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
class ApiExceptionHandler {

    @ExceptionHandler(InvalidRequestException::class)
    fun handleInvalidRequest(e: InvalidRequestException): ResponseEntity<Any> = error(HttpStatus.BAD_REQUEST, e)

    @ExceptionHandler(UnprocessableRequestException::class)
    fun handleUnprocessable(e: UnprocessableRequestException): ResponseEntity<Any> =
        error(HttpStatus.UNPROCESSABLE_ENTITY, e)

    @ExceptionHandler(UpstreamServiceException::class)
    fun handleUpstream(e: UpstreamServiceException): ResponseEntity<Any> = error(HttpStatus.BAD_GATEWAY, e)

    @ExceptionHandler(OperationFailedException::class)
    fun handleOperationFailed(e: OperationFailedException): ResponseEntity<Any> =
        error(HttpStatus.INTERNAL_SERVER_ERROR, e)

    private fun error(status: HttpStatus, e: Exception): ResponseEntity<Any> =
        ResponseEntity.status(status).body(mapOf("error" to e.message))
}
