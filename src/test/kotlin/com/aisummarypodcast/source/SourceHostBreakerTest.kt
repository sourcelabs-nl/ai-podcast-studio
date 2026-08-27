package com.aisummarypodcast.source

import com.aisummarypodcast.store.Source
import com.aisummarypodcast.store.SourceType
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * Exercises the breaker against a real Resilience4j registry rather than a mock, so the tests
 * assert the behaviour that actually ships (window filling, half-open probing, ignored exceptions)
 * instead of a restatement of the configuration.
 */
class SourceHostBreakerTest {

    private lateinit var registry: CircuitBreakerRegistry
    private lateinit var breaker: SourceHostBreaker

    /** Mirrors the `source-host` config in application.yaml, with a short wait for testability. */
    private fun config(waitInOpenState: Duration = Duration.ofMinutes(10)) = CircuitBreakerConfig.custom()
        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
        .slidingWindowSize(3)
        .minimumNumberOfCalls(3)
        .failureRateThreshold(100f)
        .waitDurationInOpenState(waitInOpenState)
        .permittedNumberOfCallsInHalfOpenState(1)
        .recordExceptions(PollFailureException.Permanent::class.java)
        .ignoreExceptions(PollFailureException.Transient::class.java)
        .build()

    @BeforeEach
    fun setUp() {
        registry = CircuitBreakerRegistry.of(mapOf("source-host" to config()))
        breaker = SourceHostBreaker(registry)
    }

    private fun source(id: String, host: String = "nitter.net") =
        Source(id = id, podcastId = "p1", type = SourceType.RSS, url = "https://$host/$id/rss")

    private val permanent = PollFailure.Permanent("HTTP 403 Forbidden")
    private val transient = PollFailure.Transient("Socket timeout")

    private fun poll(id: String, failure: PollFailure?, host: String = "nitter.net") =
        breaker.pollThroughBreaker(source(id, host)) { failure }

    @Test
    fun `permanent failures across a host open the breaker`() {
        repeat(3) { poll("s$it", permanent) }

        assertTrue(breaker.isOpen("nitter.net"))
    }

    @Test
    fun `open breaker skips further sources without polling them`() {
        repeat(3) { poll("s$it", permanent) }

        var polled = false
        val outcome = breaker.pollThroughBreaker(source("s99")) {
            polled = true
            permanent
        }

        assertInstanceOf(HostPollOutcome.Skipped::class.java, outcome)
        assertFalse(polled, "an open breaker must not issue the request")
    }

    @Test
    fun `transient failures never open the breaker`() {
        repeat(10) { poll("s$it", transient) }

        assertFalse(breaker.isOpen("nitter.net"))
    }

    // An ignored exception counts as neither success nor failure. If transient failures were merely
    // unrecorded they would count as successes and could hold open a host that never answered.
    @Test
    fun `transient failures do not mask a permanent failure`() {
        poll("s1", permanent)
        repeat(5) { poll("t$it", transient) }
        poll("s2", permanent)
        poll("s3", permanent)

        assertTrue(breaker.isOpen("nitter.net"))
    }

    @Test
    fun `successes keep the breaker closed`() {
        poll("s1", permanent)
        poll("s2", null)
        poll("s3", permanent)
        poll("s4", null)

        assertFalse(breaker.isOpen("nitter.net"))
    }

    @Test
    fun `each host gets its own breaker`() {
        repeat(3) { poll("s$it", permanent, host = "nitter.net") }

        assertTrue(breaker.isOpen("nitter.net"))
        assertFalse(breaker.isOpen("simonwillison.net"))
    }

    @Test
    fun `a healthy host still polls every source`() {
        var polls = 0
        repeat(10) {
            breaker.pollThroughBreaker(source("s$it", "example.com")) {
                polls++
                null
            }
        }

        assertEquals(10, polls)
    }

    @Test
    fun `poll outcome carries the classified failure back`() {
        val outcome = poll("s1", permanent)

        val polled = assertInstanceOf(HostPollOutcome.Polled::class.java, outcome)
        assertInstanceOf(PollFailure.Permanent::class.java, polled.failure)
    }

    @Test
    fun `successful poll reports no failure`() {
        val outcome = poll("s1", null)

        assertNull(assertInstanceOf(HostPollOutcome.Polled::class.java, outcome).failure)
    }

    /** Puts the host's breaker into half-open, where exactly one probe call is permitted. */
    private fun openThenHalfOpen(host: String = "nitter.net") {
        repeat(3) { poll("s$it", permanent, host) }
        assertTrue(breaker.isOpen(host))
        registry.circuitBreaker(host, "source-host").transitionToHalfOpenState()
    }

    @Test
    fun `a successful half-open probe closes the breaker and polling resumes`() {
        openThenHalfOpen()

        var polls = 0
        val outcomes = (1..5).map {
            breaker.pollThroughBreaker(source("probe$it")) {
                polls++
                null
            }
        }

        // The single permitted probe succeeds, which closes the breaker, so the rest poll normally.
        assertEquals(5, polls)
        assertEquals(0, outcomes.count { it is HostPollOutcome.Skipped })
        assertFalse(breaker.isTripped("nitter.net"))
    }

    @Test
    fun `a failing half-open probe reopens the breaker and the rest are skipped`() {
        openThenHalfOpen()

        var polls = 0
        val outcomes = (1..5).map {
            breaker.pollThroughBreaker(source("probe$it")) {
                polls++
                permanent
            }
        }

        assertEquals(1, polls, "only the single permitted half-open call may reach a dead host")
        assertEquals(4, outcomes.count { it is HostPollOutcome.Skipped })
        assertTrue(breaker.isOpen("nitter.net"))
    }

    @Test
    fun `a source with an unparseable url is polled without a breaker`() {
        val odd = Source(id = "s1", podcastId = "p1", type = SourceType.RSS, url = "not a url")

        var polled = false
        val outcome = breaker.pollThroughBreaker(odd) {
            polled = true
            null
        }

        assertTrue(polled)
        assertInstanceOf(HostPollOutcome.Polled::class.java, outcome)
    }

    @Test
    fun `isTripped distinguishes a closed breaker from an open one`() {
        assertFalse(breaker.isTripped("nitter.net"))

        repeat(3) { poll("s$it", permanent) }

        assertTrue(breaker.isTripped("nitter.net"))
    }

    @Test
    fun `extracts the host from a source url`() {
        assertEquals("nitter.net", extractSourceHost("https://nitter.net/sama/rss"))
    }

    @Test
    fun `returns no host for an unparseable url`() {
        assertNull(extractSourceHost("not a url at all"))
    }
}
