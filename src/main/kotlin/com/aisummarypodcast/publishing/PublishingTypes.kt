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

/**
 * The set of existing SoundCloud tracks that must be deleted to free enough upload quota for a new
 * episode, computed server-side from the live quota and the episode's duration. Presented to the
 * user for one-time consent before any track is deleted.
 */
data class QuotaDeletionPlan(
    val tracksToDelete: List<QuotaTrackToDelete>,
    val secondsToFree: Long
)

data class QuotaTrackToDelete(
    val id: Long,
    val title: String?,
    val createdAt: String?,
    val durationSeconds: Long
)

/** Thrown when SoundCloud lacks the upload quota for an episode; carries the [plan] to free space. */
class SoundCloudQuotaExceededException(
    message: String,
    val plan: QuotaDeletionPlan
) : RuntimeException(message)
