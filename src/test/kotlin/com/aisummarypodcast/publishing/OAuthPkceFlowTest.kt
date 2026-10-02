package com.aisummarypodcast.publishing

import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class OAuthPkceFlowTest {

    private class MutableClock(private var now: Instant = Instant.parse("2026-10-01T10:00:00Z")) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?): Clock = this
        override fun instant(): Instant = now
        fun advance(duration: Duration) {
            now = now.plus(duration)
        }
    }

    private val clock = MutableClock()
    private val pkceHelper = testPkceHelper()
    private val flow = OAuthPkceFlow(pkceHelper, clock)

    @Test
    fun `a valid state completes with the user and the verifier behind the challenge`() {
        val start = flow.begin("user-1")

        val result = flow.complete(start.state)

        val verified = result as OAuthPkceResult.Verified
        assertEquals("user-1", verified.userId)
        assertEquals(start.codeChallenge, pkceHelper.generateCodeChallenge(verified.codeVerifier))
    }

    @Test
    fun `a tampered state is invalid`() {
        val start = flow.begin("user-1")

        assertEquals(OAuthPkceResult.InvalidState, flow.complete(start.state + "x"))
        assertEquals(OAuthPkceResult.InvalidState, flow.complete("not-a-state"))
    }

    @Test
    fun `a state older than ten minutes is expired`() {
        val start = flow.begin("user-1")
        clock.advance(Duration.ofMinutes(11))

        assertEquals(OAuthPkceResult.Expired, flow.complete(start.state))
    }

    @Test
    fun `a state is accepted just inside the ttl`() {
        val start = flow.begin("user-1")
        clock.advance(Duration.ofMinutes(9))

        assertNotEquals(OAuthPkceResult.Expired, flow.complete(start.state))
    }

    @Test
    fun `a state can only be completed once`() {
        val start = flow.begin("user-1")

        flow.complete(start.state)

        assertEquals(OAuthPkceResult.Expired, flow.complete(start.state))
    }

    @Test
    fun `two flow instances do not accept each other's state`() {
        val other = OAuthPkceFlow(pkceHelper, clock)
        val start = flow.begin("user-1")

        assertEquals(OAuthPkceResult.Expired, other.complete(start.state))
        assertEquals("user-1", (flow.complete(start.state) as OAuthPkceResult.Verified).userId)
    }
}
