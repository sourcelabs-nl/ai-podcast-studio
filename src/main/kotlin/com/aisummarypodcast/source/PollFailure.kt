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

        /**
         * A 4xx is permanent unless the status itself says to come back later.
         *
         * Listing the permanent statuses instead and defaulting to transient is what let a lapsed
         * Narro subscription (`402 Payment Required`) retry silently for hours on 2026-09-10 while
         * the podcast lost all its X content: a status nobody had enumerated fell through to
         * transient, and a transient failure never disables a source or marks it as needing
         * attention. A client error means the request was wrong or is refused, which retrying does
         * not change, so the default belongs on the permanent side and the exceptions are the two
         * statuses that explicitly ask for a retry.
         */
        private fun classifyClientError(e: HttpClientErrorException): PollFailure {
            return when (e.statusCode.value()) {
                408 -> Transient("HTTP 408 Request Timeout")
                429 -> Transient("HTTP 429 Rate Limited")
                else -> Permanent("HTTP ${e.statusCode.value()} ${e.statusText}".trim())
            }
        }
    }
}
