package com.aisummarypodcast.publishing

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

@WebMvcTest(SoundCloudOAuthController::class)
class SoundCloudOAuthControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var userService: UserService

    @MockkBean
    private lateinit var oauthService: SoundCloudOAuthService

    @MockkBean
    private lateinit var oauthConnectionService: OAuthConnectionService

    @MockkBean(relaxed = true)
    private lateinit var appProperties: AppProperties

    private val userId = "user-1"
    private val user = User(id = userId, name = "Test User")

    private fun configureSoundCloud() {
        every { oauthService.isConfigured(any()) } returns true
        every { oauthService.authorizationUrl(any()) } returns
            "https://secure.soundcloud.com/authorize?client_id=test-client-id&state=test-state"
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
        every { oauthService.isConfigured(userId) } returns false

        mockMvc.perform(get("/users/$userId/oauth/soundcloud/authorize"))
            .andExpect(status().isServiceUnavailable)
    }

    @Test
    fun `status returns connected true when connection exists`() {
        configureSoundCloud()
        every { userService.findById(userId) } returns user
        every { oauthService.getStatus(userId) } returns SoundCloudConnectionStatus(
            OAuthConnectionStatus(connected = true, scopes = "non-expiring", connectedAt = "2026-01-01T00:00:00Z"),
            quota = SoundCloudQuota(unlimitedUploadQuota = false, uploadSecondsUsed = 10, uploadSecondsLeft = 90)
        )

        mockMvc.perform(get("/users/$userId/oauth/soundcloud/status"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.connected").value(true))
            .andExpect(jsonPath("$.scopes").value("non-expiring"))
            .andExpect(jsonPath("$.quota.uploadSecondsLeft").value(90))
    }

    @Test
    fun `status returns connected false when no connection`() {
        configureSoundCloud()
        every { userService.findById(userId) } returns user
        every { oauthService.getStatus(userId) } returns
            SoundCloudConnectionStatus(OAuthConnectionStatus(connected = false), quota = null)

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
    fun `deleteTrack returns 400 when there is no connection`() {
        configureSoundCloud()
        every { userService.findById(userId) } returns user
        every { oauthService.deleteTrack(userId, 7L) } returns false

        mockMvc.perform(delete("/users/$userId/oauth/soundcloud/tracks/7"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("No SoundCloud connection"))
    }

    @Test
    fun `deleteTrack returns the deleted track`() {
        configureSoundCloud()
        every { userService.findById(userId) } returns user
        every { oauthService.deleteTrack(userId, 7L) } returns true

        mockMvc.perform(delete("/users/$userId/oauth/soundcloud/tracks/7"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.deleted").value(true))
            .andExpect(jsonPath("$.trackId").value(7))
    }

    @Test
    fun `deleteTrack returns 500 when the client fails`() {
        configureSoundCloud()
        every { userService.findById(userId) } returns user
        every { oauthService.deleteTrack(userId, 7L) } throws RuntimeException("boom")

        mockMvc.perform(delete("/users/$userId/oauth/soundcloud/tracks/7"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.error").value("Failed to delete track: boom"))
    }

    @Test
    fun `callback with invalid state returns 400`() {
        every { oauthService.handleCallback("test-code", "invalid-state", null) } returns OAuthCallbackOutcome.InvalidState

        mockMvc.perform(get("/oauth/soundcloud/callback")
            .param("code", "test-code")
            .param("state", "invalid-state"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `callback with an expired authorization returns 400`() {
        every { oauthService.handleCallback("test-code", "state", null) } returns OAuthCallbackOutcome.Expired

        mockMvc.perform(get("/oauth/soundcloud/callback")
            .param("code", "test-code")
            .param("state", "state"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `callback that connected shows the success page`() {
        every { oauthService.handleCallback("test-code", "state", null) } returns OAuthCallbackOutcome.Connected

        mockMvc.perform(get("/oauth/soundcloud/callback")
            .param("code", "test-code")
            .param("state", "state"))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("SoundCloud connected successfully! You can close this tab.")))
    }

    @Test
    fun `callback with an error parameter renders it HTML-escaped`() {
        val error = "<script>alert(1)</script>"
        every { oauthService.handleCallback("test-code", "any-state", error) } returns OAuthCallbackOutcome.ProviderRefused(error)

        mockMvc.perform(get("/oauth/soundcloud/callback")
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

        mockMvc.perform(get("/oauth/soundcloud/callback")
            .param("state", "any-state")
            .param("error", error))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("SoundCloud authorization failed: &lt;b&gt;access_denied&lt;/b&gt;")))
    }

    @Test
    fun `callback without code and without error returns 400`() {
        every { oauthService.handleCallback(null, "any-state", null) } returns OAuthCallbackOutcome.InvalidState

        mockMvc.perform(get("/oauth/soundcloud/callback").param("state", "any-state"))
            .andExpect(status().isBadRequest)
    }
}
