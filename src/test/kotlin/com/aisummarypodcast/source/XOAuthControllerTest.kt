package com.aisummarypodcast.source

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.FeedProperties
import com.aisummarypodcast.config.OAuthPkceHelper
import com.aisummarypodcast.config.XProperties
import com.aisummarypodcast.publishing.OAuthConnectionService
import com.aisummarypodcast.publishing.OAuthConnectionStatus
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
    private lateinit var xClient: XClient

    @MockkBean
    private lateinit var oauthConnectionService: OAuthConnectionService

    @MockkBean(relaxed = true)
    private lateinit var appProperties: AppProperties

    @MockkBean
    private lateinit var pkceHelper: OAuthPkceHelper

    private val userId = "user-1"
    private val user = User(id = userId, name = "Test User")

    private fun configureX() {
        every { appProperties.x } returns XProperties(
            clientId = "test-client-id",
            clientSecret = "test-client-secret"
        )
        every { appProperties.feed } returns FeedProperties(baseUrl = "http://localhost:8085")
        every { pkceHelper.generateCodeVerifier() } returns "test-verifier"
        every { pkceHelper.generateCodeChallenge("test-verifier") } returns "test-challenge"
        every { pkceHelper.createSignedState(any()) } returns "test-state"
        every { pkceHelper.verifySignedState(any()) } returns null
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
        every { appProperties.x } returns XProperties(clientId = null, clientSecret = null)

        mockMvc.perform(get("/users/$userId/oauth/x/authorize"))
            .andExpect(status().isServiceUnavailable)
    }

    @Test
    fun `status returns connected true when connection exists`() {
        configureX()
        every { userService.findById(userId) } returns user
        every { oauthConnectionService.getStatus(userId, "x") } returns
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
        every { oauthConnectionService.getStatus(userId, "x") } returns
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
        every { pkceHelper.verifySignedState("invalid-state") } returns null

        mockMvc.perform(get("/oauth/x/callback")
            .param("code", "test-code")
            .param("state", "invalid-state"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `callback with an error parameter renders it HTML-escaped`() {
        mockMvc.perform(get("/oauth/x/callback")
            .param("code", "test-code")
            .param("state", "any-state")
            .param("error", "<script>alert(1)</script>"))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<script>"))))
    }
}
