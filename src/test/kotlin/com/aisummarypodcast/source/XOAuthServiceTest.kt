package com.aisummarypodcast.source

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.FeedProperties
import com.aisummarypodcast.config.XProperties
import com.aisummarypodcast.publishing.OAuthCallbackOutcome
import com.aisummarypodcast.publishing.OAuthConnectionService
import com.aisummarypodcast.publishing.OAuthConnectionStatus
import com.aisummarypodcast.publishing.queryParam
import com.aisummarypodcast.publishing.testPkceHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XOAuthServiceTest {

    private val appProperties = mockk<AppProperties>()
    private val xClient = mockk<XClient>()
    private val oauthConnectionService = mockk<OAuthConnectionService>(relaxed = true)
    private val service = XOAuthService(appProperties, xClient, oauthConnectionService, testPkceHelper(appProperties))

    private val userId = "user-1"

    init {
        every { appProperties.x } returns XProperties(clientId = "client-id", clientSecret = "client-secret")
        every { appProperties.feed } returns FeedProperties(baseUrl = "http://localhost:8085")
    }

    @Test
    fun `is configured only with a client id and secret`() {
        assertTrue(service.isConfigured())

        every { appProperties.x } returns XProperties(clientId = "client-id", clientSecret = " ")
        assertFalse(service.isConfigured())

        every { appProperties.x } returns XProperties(clientId = null, clientSecret = "client-secret")
        assertFalse(service.isConfigured())
    }

    @Test
    fun `authorization url carries the credentials, scopes and challenge`() {
        val url = service.authorizationUrl(userId)

        assertTrue(url.startsWith("https://twitter.com/i/oauth2/authorize?"))
        assertEquals("client-id", queryParam(url, "client_id"))
        assertEquals("http://localhost:8085/oauth/x/callback", queryParam(url, "redirect_uri"))
        assertEquals("S256", queryParam(url, "code_challenge_method"))
        assertEquals("tweet.read users.read offline.access", queryParam(url, "scope"))
    }

    @Test
    fun `a valid callback exchanges the code and stores the connection`() {
        val state = queryParam(service.authorizationUrl(userId), "state")!!
        every {
            xClient.exchangeCodeForTokens("the-code", "client-id", "client-secret", "http://localhost:8085/oauth/x/callback", any())
        } returns XTokenResponse(accessToken = "access", refreshToken = "refresh", expiresIn = 7200, scope = "tweet.read")

        val outcome = service.handleCallback("the-code", state, null)

        assertEquals(OAuthCallbackOutcome.Connected, outcome)
        verify { oauthConnectionService.storeConnection(userId, "x", "access", "refresh", any(), "tweet.read") }
    }

    @Test
    fun `a provider error is reported even without code and state`() {
        assertEquals(OAuthCallbackOutcome.ProviderRefused("access_denied"), service.handleCallback(null, null, "access_denied"))
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
        val state = queryParam(service.authorizationUrl(userId), "state")!!
        every { xClient.exchangeCodeForTokens(any(), any(), any(), any(), any()) } returns XTokenResponse(accessToken = "access")
        service.handleCallback("code", state, null)

        assertEquals(OAuthCallbackOutcome.Expired, service.handleCallback("code", state, null))
    }

    @Test
    fun `a failing token exchange is an exchange failure and stores nothing`() {
        val state = queryParam(service.authorizationUrl(userId), "state")!!
        every { xClient.exchangeCodeForTokens(any(), any(), any(), any(), any()) } throws RuntimeException("rejected")

        assertEquals(OAuthCallbackOutcome.ExchangeFailed, service.handleCallback("code", state, null))
        verify(exactly = 0) { oauthConnectionService.storeConnection(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `status is read from the connection service`() {
        val status = OAuthConnectionStatus(connected = true, scopes = "tweet.read")
        every { oauthConnectionService.getStatus(userId, "x") } returns status

        assertEquals(status, service.getStatus(userId))
    }
}
