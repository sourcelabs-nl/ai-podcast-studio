package com.aisummarypodcast.publishing

/** What [OAuthPkceFlow.begin] hands back: the signed state and the challenge for the authorize URL. */
data class OAuthPkceStart(
    val state: String,
    val codeChallenge: String
)

/** The result of checking a callback state against the pending authorizations of one provider. */
sealed interface OAuthPkceResult {
    data class Verified(val userId: String, val codeVerifier: String) : OAuthPkceResult
    data object InvalidState : OAuthPkceResult
    data object Expired : OAuthPkceResult
}

/** How an OAuth provider redirect ended, independent of how it is shown to the browser. */
sealed interface OAuthCallbackOutcome {
    data object Connected : OAuthCallbackOutcome
    data class ProviderRefused(val error: String) : OAuthCallbackOutcome
    data object InvalidState : OAuthCallbackOutcome
    data object Expired : OAuthCallbackOutcome
    data object ExchangeFailed : OAuthCallbackOutcome
}

/** The wording of the callback pages for one provider. */
data class OAuthProviderPages(
    val title: String,
    val connectedMessage: String,
    val failedMessage: String
) {
    companion object {
        val SOUNDCLOUD = OAuthProviderPages(
            title = "SoundCloud",
            connectedMessage = "SoundCloud connected successfully! You can close this tab.",
            failedMessage = "Failed to connect SoundCloud. Please try again."
        )
        val X = OAuthProviderPages(
            title = "X",
            connectedMessage = "X account connected successfully! You can close this tab.",
            failedMessage = "Failed to connect X account. Please try again."
        )
    }
}

/** The SoundCloud connection status together with the account quota, when it could be fetched. */
data class SoundCloudConnectionStatus(
    val connection: OAuthConnectionStatus,
    val quota: SoundCloudQuota?
)
