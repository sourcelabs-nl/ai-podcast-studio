package com.aisummarypodcast.publishing

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SoundCloudOAuthServiceTest {

    private val credentialResolver = mockk<SoundCloudCredentialResolver>()
    private val soundCloudClient = mockk<SoundCloudClient>()
    private val oauthConnectionService = mockk<OAuthConnectionService>(relaxed = true)
    private val service = SoundCloudOAuthService(
        credentialResolver, soundCloudClient, oauthConnectionService, testPkceHelper()
    )

    private val userId = "user-1"
    private val credentials = SoundCloudCredentials("client-id", "client-secret", "http://localhost/cb")

    private fun authorizeAndGetState(): String {
        every { credentialResolver.resolve(userId) } returns credentials
        return queryParam(service.authorizationUrl(userId), "state")!!
    }

    @Test
    fun `authorization url carries the credentials, scope and challenge`() {
        every { credentialResolver.resolve(userId) } returns credentials

        val url = service.authorizationUrl(userId)

        assertTrue(url.startsWith("https://secure.soundcloud.com/authorize?"))
        assertEquals("client-id", queryParam(url, "client_id"))
        assertEquals("http://localhost/cb", queryParam(url, "redirect_uri"))
        assertEquals("code", queryParam(url, "response_type"))
        assertEquals("S256", queryParam(url, "code_challenge_method"))
        assertEquals("non-expiring", queryParam(url, "scope"))
        assertEquals(43, queryParam(url, "code_challenge")!!.length)
    }

    @Test
    fun `a valid callback exchanges the code and stores the connection`() {
        val state = authorizeAndGetState()
        val verifier = slot<String>()
        every {
            soundCloudClient.exchangeCodeForTokens("the-code", "client-id", "client-secret", "http://localhost/cb", capture(verifier))
        } returns SoundCloudTokenResponse(accessToken = "access", refreshToken = "refresh", expiresIn = 3600, scope = "non-expiring")

        val outcome = service.handleCallback("the-code", state, null)

        assertEquals(OAuthCallbackOutcome.Connected, outcome)
        assertTrue(verifier.captured.isNotBlank())
        verify {
            oauthConnectionService.storeConnection(userId, "soundcloud", "access", "refresh", match<Instant> { it.isAfter(Instant.now()) }, "non-expiring")
        }
    }

    @Test
    fun `a provider error is reported without touching state or client`() {
        val outcome = service.handleCallback(null, null, "access_denied")

        assertEquals(OAuthCallbackOutcome.ProviderRefused("access_denied"), outcome)
    }

    @Test
    fun `a provider error wins over an invalid state`() {
        assertEquals(
            OAuthCallbackOutcome.ProviderRefused("access_denied"),
            service.handleCallback("code", "bogus", "access_denied")
        )
    }

    @Test
    fun `a missing code or state without an error is an invalid state`() {
        assertEquals(OAuthCallbackOutcome.InvalidState, service.handleCallback(null, "state", null))
        assertEquals(OAuthCallbackOutcome.InvalidState, service.handleCallback("code", null, null))
    }

    @Test
    fun `a tampered state is an invalid state`() {
        assertEquals(OAuthCallbackOutcome.InvalidState, service.handleCallback("code", "bogus", null))
    }

    @Test
    fun `a state that was already used is expired`() {
        val state = authorizeAndGetState()
        every { soundCloudClient.exchangeCodeForTokens(any(), any(), any(), any(), any()) } returns
            SoundCloudTokenResponse(accessToken = "access")
        service.handleCallback("code", state, null)

        assertEquals(OAuthCallbackOutcome.Expired, service.handleCallback("code", state, null))
    }

    @Test
    fun `a failing token exchange is an exchange failure and stores nothing`() {
        val state = authorizeAndGetState()
        every { soundCloudClient.exchangeCodeForTokens(any(), any(), any(), any(), any()) } throws RuntimeException("rejected")

        assertEquals(OAuthCallbackOutcome.ExchangeFailed, service.handleCallback("code", state, null))
        verify(exactly = 0) { oauthConnectionService.storeConnection(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `status of a disconnected account has no quota`() {
        every { oauthConnectionService.getStatus(userId, "soundcloud") } returns OAuthConnectionStatus(connected = false)

        val status = service.getStatus(userId)

        assertFalse(status.connection.connected)
        assertNull(status.quota)
    }

    @Test
    fun `status of a connected account includes the quota`() {
        val quota = SoundCloudQuota(unlimitedUploadQuota = false, uploadSecondsUsed = 10, uploadSecondsLeft = 90)
        every { oauthConnectionService.getStatus(userId, "soundcloud") } returns OAuthConnectionStatus(connected = true, scopes = "non-expiring")
        every { oauthConnectionService.getConnection(userId, "soundcloud") } returns decryptedConnection()
        every { soundCloudClient.getMe("access") } returns SoundCloudMeResponse(id = 1, quota = quota)

        assertEquals(quota, service.getStatus(userId).quota)
    }

    @Test
    fun `status of a connected account survives a failing quota lookup`() {
        every { oauthConnectionService.getStatus(userId, "soundcloud") } returns OAuthConnectionStatus(connected = true)
        every { oauthConnectionService.getConnection(userId, "soundcloud") } returns decryptedConnection()
        every { soundCloudClient.getMe(any()) } throws RuntimeException("down")

        val status = service.getStatus(userId)

        assertTrue(status.connection.connected)
        assertNull(status.quota)
    }

    @Test
    fun `deleteTrack returns false without a connection`() {
        every { oauthConnectionService.getConnection(userId, "soundcloud") } returns null

        assertFalse(service.deleteTrack(userId, 7L))
    }

    @Test
    fun `deleteTrack deletes through the client`() {
        every { oauthConnectionService.getConnection(userId, "soundcloud") } returns decryptedConnection()
        every { soundCloudClient.deleteTrack("access", 7L) } returns Unit

        assertTrue(service.deleteTrack(userId, 7L))
        verify { soundCloudClient.deleteTrack("access", 7L) }
    }

    private fun decryptedConnection() = DecryptedOAuthConnection(
        userId = userId,
        provider = "soundcloud",
        accessToken = "access",
        refreshToken = null,
        expiresAt = null,
        scopes = null,
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z"
    )
}
