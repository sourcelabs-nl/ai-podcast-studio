package com.aisummarypodcast.source

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.OAuthPkceHelper
import com.aisummarypodcast.publishing.OAuthCallbackOutcome
import com.aisummarypodcast.publishing.OAuthConnectionService
import com.aisummarypodcast.publishing.OAuthConnectionStatus
import com.aisummarypodcast.publishing.OAuthPkceFlow
import com.aisummarypodcast.publishing.OAuthPkceResult
import com.aisummarypodcast.publishing.buildAuthorizationUrl
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class XOAuthService(
    private val appProperties: AppProperties,
    private val xClient: XClient,
    private val oauthConnectionService: OAuthConnectionService,
    pkceHelper: OAuthPkceHelper
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val pkceFlow = OAuthPkceFlow(pkceHelper)

    fun isConfigured(): Boolean =
        !appProperties.x.clientId.isNullOrBlank() &&
            !appProperties.x.clientSecret.isNullOrBlank()

    fun authorizationUrl(userId: String): String {
        val start = pkceFlow.begin(userId)

        return buildAuthorizationUrl(
            AUTHORIZE_ENDPOINT,
            mapOf(
                "client_id" to appProperties.x.clientId!!,
                "redirect_uri" to redirectUri(),
                "response_type" to "code",
                "code_challenge" to start.codeChallenge,
                "code_challenge_method" to "S256",
                "state" to start.state,
                "scope" to "tweet.read users.read offline.access"
            )
        )
    }

    fun handleCallback(code: String?, state: String?, error: String?): OAuthCallbackOutcome {
        if (error != null) return OAuthCallbackOutcome.ProviderRefused(error)
        if (code == null || state == null) return OAuthCallbackOutcome.InvalidState

        val verified = when (val result = pkceFlow.complete(state)) {
            OAuthPkceResult.InvalidState -> return OAuthCallbackOutcome.InvalidState
            OAuthPkceResult.Expired -> return OAuthCallbackOutcome.Expired
            is OAuthPkceResult.Verified -> result
        }

        return try {
            exchangeAndStore(code, verified)
            log.info("X account connected for user {}", verified.userId)
            OAuthCallbackOutcome.Connected
        } catch (e: Exception) {
            log.error("Failed to exchange X authorization code for user {}: {}", verified.userId, e.message, e)
            OAuthCallbackOutcome.ExchangeFailed
        }
    }

    fun getStatus(userId: String): OAuthConnectionStatus =
        oauthConnectionService.getStatus(userId, PROVIDER)

    private fun exchangeAndStore(code: String, verified: OAuthPkceResult.Verified) {
        val tokenResponse = xClient.exchangeCodeForTokens(
            code = code,
            clientId = appProperties.x.clientId!!,
            clientSecret = appProperties.x.clientSecret!!,
            redirectUri = redirectUri(),
            codeVerifier = verified.codeVerifier
        )

        oauthConnectionService.storeConnection(
            userId = verified.userId,
            provider = PROVIDER,
            accessToken = tokenResponse.accessToken,
            refreshToken = tokenResponse.refreshToken,
            expiresAt = tokenResponse.expiresIn?.let { Instant.now().plusSeconds(it) },
            scopes = tokenResponse.scope
        )
    }

    private fun redirectUri(): String = "${appProperties.feed.baseUrl}/oauth/x/callback"

    private companion object {
        const val PROVIDER = "x"
        const val AUTHORIZE_ENDPOINT = "https://twitter.com/i/oauth2/authorize"
    }
}
