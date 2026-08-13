package com.aisummarypodcast.publishing

import org.apache.commons.net.ftp.FTPClient
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
    val useTls: Boolean = true,
    /** Free-form so an unknown value degrades to the default instead of rejecting the request. */
    val transferMode: String? = null
)

/**
 * How the FTP data connection is opened. Passive works on most networks; active is for networks that
 * block outbound data connections but allow the server to connect back to the client.
 */
enum class FtpTransferMode {
    PASSIVE,
    ACTIVE;

    fun opposite(): FtpTransferMode = if (this == PASSIVE) ACTIVE else PASSIVE

    companion object {
        /** Tolerant parse: absent, blank, or unrecognized values mean [PASSIVE], the historical behavior. */
        fun from(value: String?): FtpTransferMode =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: PASSIVE
    }
}

/** Everything needed to open one FTP(S) connection. */
data class FtpConnectionSettings(
    val host: String,
    val port: Int = 21,
    val username: String,
    val password: String,
    val useTls: Boolean = true,
    val transferMode: FtpTransferMode = FtpTransferMode.PASSIVE
)

/** The stage of the FTP conversation that a failure belongs to, used to explain what went wrong. */
enum class FtpPhase {
    CONTROL_CONNECTION,
    TLS,
    AUTHENTICATION,
    DATA_CHANNEL
}

/**
 * A connected client together with the transfer mode that actually worked. [fellBackFrom] is set when
 * the configured mode could not open a data connection and the other mode was used instead, which is
 * a hint to the user that their stored setting is wrong for this network.
 */
data class FtpConnection(
    val client: FTPClient,
    val transferMode: FtpTransferMode,
    val fellBackFrom: FtpTransferMode? = null,
    val fallbackReason: String? = null
)

internal data class PendingOAuth(
    val codeVerifier: String,
    val createdAt: Instant = Instant.now()
)
