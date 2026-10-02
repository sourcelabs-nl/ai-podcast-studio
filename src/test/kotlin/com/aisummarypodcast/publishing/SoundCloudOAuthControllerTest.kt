package com.aisummarypodcast.publishing

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.OAuthPkceHelper
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

@WebMvcTest(SoundCloudOAuthController::class)
class SoundCloudOAuthControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var userService: UserService

    @MockkBean
    private lateinit var soundCloudClient: SoundCloudClient

    @MockkBean
    private lateinit var oauthConnectionService: OAuthConnectionService

    @MockkBean
    private lateinit var credentialResolver: SoundCloudCredentialResolver

    @MockkBean(relaxed = true)
    private lateinit var appProperties: AppProperties

    @MockkBean
    private lateinit var pkceHelper: OAuthPkceHelper

    private val userId = "user-1"
    private val user = User(id = userId, name = "Test User")

    private fun configureSoundCloud() {
        every { credentialResolver.resolve(any()) } returns SoundCloudCredentials(
            clientId = "test-client-id",
            clientSecret = "test-client-secret",
            callbackUri = "http://localhost:8085/oauth/soundcloud/callback"
        )
        every { credentialResolver.isConfigured(any()) } returns true
        every { pkceHelper.generateCodeVerifier() } returns "test-verifier"
        every { pkceHelper.generateCodeChallenge("test-verifier") } returns "test-challenge"
        every { pkceHelper.createSignedState(any()) } returns "test-state"
        every { pkceHelper.verifySignedState(any()) } returns null
    }

    @Test
    fun `authorize returns authorization URL`() {
        configureSoundCloud()
        every { userService.findById(userId) } returns user

        mockMvc.perform(get("/users/$userId/oauth/soundcloud/authorize"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.authorizationUrl").exists())
            .andExpect(jsonPath("$.authorizationUrl").value(
                org.hamcrest.Matchers.containsString("https://secure.soundcloud.com/authorize")
            ))
            .andExpect(jsonPath("$.authorizationUrl").value(
                org.hamcrest.Matchers.containsString("client_id=test-client-id")
            ))
    }

    @Test
    fun `authorize returns 404 for unknown user`() {
        configureSoundCloud()
        every { userService.findById(userId) } returns null

        mockMvc.perform(get("/users/$userId/oauth/soundcloud/authorize"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `authorize returns 503 when not configured`() {
        every { userService.findById(userId) } returns user
        every { credentialResolver.resolve(userId) } throws IllegalStateException("Not configured")

        mockMvc.perform(get("/users/$userId/oauth/soundcloud/authorize"))
            .andExpect(status().isServiceUnavailable)
    }

    @Test
    fun `status returns connected true when connection exists`() {
        configureSoundCloud()
        every { userService.findById(userId) } returns user
        every { oauthConnectionService.getStatus(userId, "soundcloud") } returns
            OAuthConnectionStatus(connected = true, scopes = "non-expiring", connectedAt = "2026-01-01T00:00:00Z")

        mockMvc.perform(get("/users/$userId/oauth/soundcloud/status"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.connected").value(true))
            .andExpect(jsonPath("$.scopes").value("non-expiring"))
    }

    @Test
    fun `status returns connected false when no connection`() {
        configureSoundCloud()
        every { userService.findById(userId) } returns user
        every { oauthConnectionService.getStatus(userId, "soundcloud") } returns
            OAuthConnectionStatus(connected = false)

        mockMvc.perform(get("/users/$userId/oauth/soundcloud/status"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.connected").value(false))
    }

    @Test
    fun `status returns 404 for unknown user`() {
        configureSoundCloud()
        every { userService.findById(userId) } returns null

        mockMvc.perform(get("/users/$userId/oauth/soundcloud/status"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `disconnect returns 204 when connection deleted`() {
        configureSoundCloud()
        every { userService.findById(userId) } returns user
        every { oauthConnectionService.deleteConnection(userId, "soundcloud") } returns true

        mockMvc.perform(delete("/users/$userId/oauth/soundcloud"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `disconnect returns 404 when no connection`() {
        configureSoundCloud()
        every { userService.findById(userId) } returns user
        every { oauthConnectionService.deleteConnection(userId, "soundcloud") } returns false

        mockMvc.perform(delete("/users/$userId/oauth/soundcloud"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `callback with invalid state returns 400`() {
        every { pkceHelper.verifySignedState("invalid-state") } returns null

        mockMvc.perform(get("/oauth/soundcloud/callback")
            .param("code", "test-code")
            .param("state", "invalid-state"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `callback with an error parameter renders it HTML-escaped`() {
        mockMvc.perform(get("/oauth/soundcloud/callback")
            .param("code", "test-code")
            .param("state", "any-state")
            .param("error", "<script>alert(1)</script>"))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<script>"))))
    }
}
