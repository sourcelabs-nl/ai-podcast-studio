package com.aisummarypodcast.publishing

import java.time.Instant

data class DecryptedOAuthConnection(
    val userId: String,
    val provider: String,
    val accessToken: String,
    val refreshToken: String?,
    val expiresAt: Instant?,
    val scopes: String?,
    val createdAt: String,
    val updatedAt: String
)

data class OAuthConnectionStatus(
    val connected: Boolean,
    val scopes: String? = null,
    val connectedAt: String? = null
)

data class TestConnectionResult(
    val success: Boolean,
    val message: String,
    val quota: Map<String, Any>? = null
)

data class FtpTestCredentials(
    val host: String,
    val port: Int = 21,
    val username: String,
    val password: String? = null,
    val useTls: Boolean = true
)

internal data class PendingOAuth(
    val codeVerifier: String,
    val createdAt: Instant = Instant.now()
)
