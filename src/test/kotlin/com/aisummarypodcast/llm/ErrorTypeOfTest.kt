package com.aisummarypodcast.llm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.web.client.ResourceAccessException
import java.net.SocketTimeoutException

/**
 * Covers the one distinction the latency read depends on: a request that ran out of time counts as
 * latency and every other failure does not, so a timeout has to be recognisable without matching on
 * an implementation type's name.
 */
class ErrorTypeOfTest {

    @Test
    fun `a socket timeout is recorded as a timeout`() {
        assertEquals(TIMEOUT_ERROR_TYPE, errorTypeOf(SocketTimeoutException("Read timed out")))
    }

    @Test
    fun `a timeout wrapped by the HTTP client is still a timeout`() {
        val wrapped = ResourceAccessException("I/O error", SocketTimeoutException("Read timed out"))

        assertEquals(TIMEOUT_ERROR_TYPE, errorTypeOf(wrapped))
    }

    @Test
    fun `a provider rejection keeps its own type`() {
        assertEquals(
            "JevTransientException",
            errorTypeOf(JevTransientException("Jev decisions endpoint returned HTTP 529"))
        )
    }

    @Test
    fun `a cause chain that loops terminates`() {
        val looping = object : RuntimeException("loops") {
            override val cause: Throwable get() = this
        }

        assertEquals(looping.javaClass.simpleName, errorTypeOf(looping))
    }
}
