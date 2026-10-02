package com.aisummarypodcast.publishing

import com.aisummarypodcast.api.OperationFailedException
import com.aisummarypodcast.config.OAuthPkceHelper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class SoundCloudOAuthService(
    private val credentialResolver: SoundCloudCredentialResolver,
    private val soundCloudClient: SoundCloudClient,
    private val oauthConnectionService: OAuthConnectionService,
    pkceHelper: OAuthPkceHelper
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val pkceFlow = OAuthPkceFlow(pkceHelper)

    fun isConfigured(userId: String): Boolean = credentialResolver.isConfigured(userId)

    /** @throws IllegalStateException when no SoundCloud credentials are configured for the user */
    fun authorizationUrl(userId: String): String {
        val credentials = credentialResolver.resolve(userId)
        val start = pkceFlow.begin(userId)

        return buildAuthorizationUrl(
            AUTHORIZE_ENDPOINT,
            mapOf(
                "client_id" to credentials.clientId,
                "redirect_uri" to credentials.callbackUri,
                "response_type" to "code",
                "code_challenge" to start.codeChallenge,
                "code_challenge_method" to "S256",
                "state" to start.state,
                "scope" to "non-expiring"
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
            log.info("SoundCloud account connected for user {}", verified.userId)
            OAuthCallbackOutcome.Connected
        } catch (e: Exception) {
            log.error("Failed to exchange SoundCloud authorization code for user {}: {}", verified.userId, e.message, e)
            OAuthCallbackOutcome.ExchangeFailed
        }
    }

    fun getStatus(userId: String): SoundCloudConnectionStatus {
        val status = oauthConnectionService.getStatus(userId, PROVIDER)
        if (!status.connected) return SoundCloudConnectionStatus(status, quota = null)

        val quota = try {
            val connection = oauthConnectionService.getConnection(userId, PROVIDER)!!
            soundCloudClient.getMe(connection.accessToken).quota
        } catch (e: Exception) {
            log.warn("Failed to fetch SoundCloud quota for user {}: {}", userId, e.message)
            null
        }
        return SoundCloudConnectionStatus(status, quota)
    }

    /**
     * Deletes the track on SoundCloud.
     *
     * @return false when the user has no SoundCloud connection
     */
    fun deleteTrack(userId: String, trackId: Long): Boolean {
        val connection = oauthConnectionService.getConnection(userId, PROVIDER) ?: return false
        try {
            soundCloudClient.deleteTrack(connection.accessToken, trackId)
        } catch (e: Exception) {
            log.error("Failed to delete SoundCloud track {} for user {}: {}", trackId, userId, e.message, e)
            throw OperationFailedException("Failed to delete track: ${e.message}", e)
        }
        log.info("Deleted SoundCloud track {} for user {}", trackId, userId)
        return true
    }

    private fun exchangeAndStore(code: String, verified: OAuthPkceResult.Verified) {
        val credentials = credentialResolver.resolve(verified.userId)
        val tokenResponse = soundCloudClient.exchangeCodeForTokens(
            code = code,
            clientId = credentials.clientId,
            clientSecret = credentials.clientSecret,
            redirectUri = credentials.callbackUri,
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

    private companion object {
        const val PROVIDER = "soundcloud"
        const val AUTHORIZE_ENDPOINT = "https://secure.soundcloud.com/authorize"
    }
}
