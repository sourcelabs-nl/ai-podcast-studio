package com.aisummarypodcast.source

import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class PollFailureTest {

    @Test
    fun `HTTP 404 is classified as permanent`() {
        val exception = HttpClientErrorException(HttpStatus.NOT_FOUND)
        assertInstanceOf(PollFailure.Permanent::class.java, PollFailure.classify(exception))
    }

    @Test
    fun `HTTP 410 is classified as permanent`() {
        val exception = HttpClientErrorException(HttpStatus.GONE)
        assertInstanceOf(PollFailure.Permanent::class.java, PollFailure.classify(exception))
    }

    @Test
    fun `HTTP 401 is classified as permanent`() {
        val exception = HttpClientErrorException(HttpStatus.UNAUTHORIZED)
        assertInstanceOf(PollFailure.Permanent::class.java, PollFailure.classify(exception))
    }

    @Test
    fun `HTTP 403 is classified as permanent`() {
        val exception = HttpClientErrorException(HttpStatus.FORBIDDEN)
        assertInstanceOf(PollFailure.Permanent::class.java, PollFailure.classify(exception))
    }

    @Test
    fun `UnknownHostException is classified as permanent`() {
        val exception = UnknownHostException("example.com")
        assertInstanceOf(PollFailure.Permanent::class.java, PollFailure.classify(exception))
    }

    @Test
    fun `HTTP 429 is classified as transient`() {
        val exception = HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS)
        assertInstanceOf(PollFailure.Transient::class.java, PollFailure.classify(exception))
    }

    @Test
    fun `HTTP 500 is classified as transient`() {
        val exception = HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)
        assertInstanceOf(PollFailure.Transient::class.java, PollFailure.classify(exception))
    }

    @Test
    fun `HTTP 503 is classified as transient`() {
        val exception = HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE)
        assertInstanceOf(PollFailure.Transient::class.java, PollFailure.classify(exception))
    }

    @Test
    fun `SocketTimeoutException is classified as transient`() {
        val exception = SocketTimeoutException("Read timed out")
        assertInstanceOf(PollFailure.Transient::class.java, PollFailure.classify(exception))
    }

    @Test
    fun `ConnectException is classified as transient`() {
        val exception = ConnectException("Connection refused")
        assertInstanceOf(PollFailure.Transient::class.java, PollFailure.classify(exception))
    }

    @Test
    fun `unknown exception is classified as transient`() {
        val exception = IllegalStateException("Something unexpected")
        assertInstanceOf(PollFailure.Transient::class.java, PollFailure.classify(exception))
    }

    @Test
    fun `wrapped UnknownHostException is classified as permanent`() {
        val cause = UnknownHostException("example.com")
        val wrapper = RuntimeException("Fetch failed", cause)
        assertInstanceOf(PollFailure.Permanent::class.java, PollFailure.classify(wrapper))
    }

    @Test
    fun `HTTP 402 is classified as permanent`() {
        // A lapsed subscription: the exact status that fell through to transient and let the Narro
        // feeds retry silently for hours on 2026-09-10.
        val exception = HttpClientErrorException(HttpStatus.PAYMENT_REQUIRED)
        assertInstanceOf(PollFailure.Permanent::class.java, PollFailure.classify(exception))
    }

    @Test
    fun `an unenumerated client error is classified as permanent`() {
        val exception = HttpClientErrorException(HttpStatus.I_AM_A_TEAPOT)
        assertInstanceOf(PollFailure.Permanent::class.java, PollFailure.classify(exception))
    }

    @Test
    fun `HTTP 408 is classified as transient`() {
        val exception = HttpClientErrorException(HttpStatus.REQUEST_TIMEOUT)
        assertInstanceOf(PollFailure.Transient::class.java, PollFailure.classify(exception))
    }
}
