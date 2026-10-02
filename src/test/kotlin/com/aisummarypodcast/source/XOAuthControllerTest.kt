package com.aisummarypodcast.source

import com.aisummarypodcast.publishing.OAuthCallbackOutcome
import com.aisummarypodcast.publishing.OAuthConnectionService
import com.aisummarypodcast.publishing.OAuthConnectionStatus
import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.store.User
import com.aisummarypodcast.user.UserService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(XOAuthController::class)
class XOAuthControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var userService: UserService

    @MockkBean
    private lateinit var oauthService: XOAuthService

    @MockkBean
    private lateinit var oauthConnectionService: OAuthConnectionService

    @MockkBean(relaxed = true)
    private lateinit var appProperties: AppProperties

    private val userId = "user-1"
    private val user = User(id = userId, name = "Test User")

    private fun configureX() {
        every { oauthService.isConfigured() } returns true
        every { oauthService.authorizationUrl(any()) } returns
            "https://twitter.com/i/oauth2/authorize?client_id=test-client-id&scope=tweet.read+users.read+offline.access"
    }

    @Test
    fun `authorize returns authorization URL`() {
        configureX()
        every { userService.findById(userId) } returns user

        mockMvc.perform(get("/users/$userId/oauth/x/authorize"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.authorizationUrl").exists())
            .andExpect(jsonPath("$.authorizationUrl").value(
                org.hamcrest.Matchers.containsString("https://twitter.com/i/oauth2/authorize")
            ))
            .andExpect(jsonPath("$.authorizationUrl").value(
                org.hamcrest.Matchers.containsString("client_id=test-client-id")
            ))
            .andExpect(jsonPath("$.authorizationUrl").value(
                org.hamcrest.Matchers.containsString("scope=tweet.read")
            ))
    }

    @Test
    fun `authorize returns 404 for unknown user`() {
        configureX()
        every { userService.findById(userId) } returns null

        mockMvc.perform(get("/users/$userId/oauth/x/authorize"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `authorize returns 503 when not configured`() {
        every { oauthService.isConfigured() } returns false

        mockMvc.perform(get("/users/$userId/oauth/x/authorize"))
            .andExpect(status().isServiceUnavailable)
    }

    @Test
    fun `status returns connected true when connection exists`() {
        configureX()
        every { userService.findById(userId) } returns user
        every { oauthService.getStatus(userId) } returns
            OAuthConnectionStatus(connected = true, scopes = "tweet.read users.read offline.access", connectedAt = "2026-01-01T00:00:00Z")

        mockMvc.perform(get("/users/$userId/oauth/x/status"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.connected").value(true))
            .andExpect(jsonPath("$.scopes").value("tweet.read users.read offline.access"))
    }

    @Test
    fun `status returns connected false when no connection`() {
        configureX()
        every { userService.findById(userId) } returns user
        every { oauthService.getStatus(userId) } returns
            OAuthConnectionStatus(connected = false)

        mockMvc.perform(get("/users/$userId/oauth/x/status"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.connected").value(false))
    }

    @Test
    fun `disconnect returns 204 when connection deleted`() {
        configureX()
        every { userService.findById(userId) } returns user
        every { oauthConnectionService.deleteConnection(userId, "x") } returns true

        mockMvc.perform(delete("/users/$userId/oauth/x"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `disconnect returns 404 when no connection`() {
        configureX()
        every { userService.findById(userId) } returns user
        every { oauthConnectionService.deleteConnection(userId, "x") } returns false

        mockMvc.perform(delete("/users/$userId/oauth/x"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `callback with invalid state returns 400`() {
        every { oauthService.handleCallback("test-code", "invalid-state", null) } returns OAuthCallbackOutcome.InvalidState

        mockMvc.perform(get("/oauth/x/callback")
            .param("code", "test-code")
            .param("state", "invalid-state"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `callback with an expired authorization returns 400`() {
        every { oauthService.handleCallback("test-code", "state", null) } returns OAuthCallbackOutcome.Expired

        mockMvc.perform(get("/oauth/x/callback")
            .param("code", "test-code")
            .param("state", "state"))
            .andExpect(status().isBadRequest)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("The authorization request has expired. Please try again.")))
    }

    @Test
    fun `callback that connected shows the success page`() {
        every { oauthService.handleCallback("test-code", "state", null) } returns OAuthCallbackOutcome.Connected

        mockMvc.perform(get("/oauth/x/callback")
            .param("code", "test-code")
            .param("state", "state"))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("X account connected successfully! You can close this tab.")))
    }

    @Test
    fun `callback with an error parameter renders it HTML-escaped`() {
        val error = "<script>alert(1)</script>"
        every { oauthService.handleCallback("test-code", "any-state", error) } returns OAuthCallbackOutcome.ProviderRefused(error)

        mockMvc.perform(get("/oauth/x/callback")
            .param("code", "test-code")
            .param("state", "any-state")
            .param("error", error))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<script>"))))
    }

    @Test
    fun `callback with an error and no code shows the escaped failure page`() {
        val error = "<b>access_denied</b>"
        every { oauthService.handleCallback(null, "any-state", error) } returns OAuthCallbackOutcome.ProviderRefused(error)

        mockMvc.perform(get("/oauth/x/callback")
            .param("state", "any-state")
            .param("error", error))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("X authorization failed: &lt;b&gt;access_denied&lt;/b&gt;")))
    }

    @Test
    fun `callback without code and without error returns 400`() {
        every { oauthService.handleCallback(null, "any-state", null) } returns OAuthCallbackOutcome.InvalidState

        mockMvc.perform(get("/oauth/x/callback").param("state", "any-state"))
            .andExpect(status().isBadRequest)
    }
}
