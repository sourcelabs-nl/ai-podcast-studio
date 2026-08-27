package com.aisummarypodcast.source

import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * A classified poll failure. [label] is the value persisted to `Source.lastFailureType`, defined
 * here so producers (the poller) and consumers (the host circuit breaker) share one definition
 * rather than each repeating the string.
 */
sealed class PollFailure(val message: String, val label: String) {
    class Transient(message: String) : PollFailure(message, TRANSIENT)
    class Permanent(message: String) : PollFailure(message, PERMANENT)

    companion object {
        const val PERMANENT = "permanent"
        const val TRANSIENT = "transient"

        fun classify(exception: Exception): PollFailure {
            return when (exception) {
                is HttpClientErrorException -> classifyClientError(exception)
                is HttpServerErrorException -> Transient("HTTP ${exception.statusCode.value()}")
                is UnknownHostException -> Permanent("DNS resolution failed")
                is SocketTimeoutException -> Transient("Socket timeout")
                is ConnectException -> Transient("Connection refused")
                else -> {
                    val cause = exception.cause
                    if (cause is Exception && cause !== exception) classify(cause)
                    else Transient(exception.message ?: "Unknown error")
                }
            }
        }

        private fun classifyClientError(e: HttpClientErrorException): PollFailure {
            return when (e.statusCode.value()) {
                404 -> Permanent("HTTP 404 Not Found")
                410 -> Permanent("HTTP 410 Gone")
                401 -> Permanent("HTTP 401 Unauthorized")
                403 -> Permanent("HTTP 403 Forbidden")
                429 -> Transient("HTTP 429 Rate Limited")
                else -> Transient("HTTP ${e.statusCode.value()}")
            }
        }
    }
}
