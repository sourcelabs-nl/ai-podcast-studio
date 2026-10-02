package com.aisummarypodcast.publishing

import com.aisummarypodcast.config.OAuthPkceHelper
import java.time.Clock
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Holds the PKCE verifiers of the authorizations one provider has started and checks the state of its callbacks.
 *
 * Deliberately not a bean: every provider service creates its own instance. The state is signed with one
 * key for all providers, so a shared verifier map would let a state issued for one provider complete
 * the callback of another.
 */
class OAuthPkceFlow(
    private val pkceHelper: OAuthPkceHelper,
    private val clock: Clock = Clock.systemUTC()
) {

    private val pending = ConcurrentHashMap<String, PendingOAuth>()

    fun begin(userId: String): OAuthPkceStart {
        removeExpired()

        val codeVerifier = pkceHelper.generateCodeVerifier()
        val state = pkceHelper.createSignedState(userId)
        pending[state] = PendingOAuth(codeVerifier, clock.instant())

        return OAuthPkceStart(state, pkceHelper.generateCodeChallenge(codeVerifier))
    }

    fun complete(state: String): OAuthPkceResult {
        val userId = pkceHelper.verifySignedState(state) ?: return OAuthPkceResult.InvalidState
        val entry = pending.remove(state) ?: return OAuthPkceResult.Expired
        if (isExpired(entry)) return OAuthPkceResult.Expired
        return OAuthPkceResult.Verified(userId, entry.codeVerifier)
    }

    private fun removeExpired() {
        pending.entries.removeIf { isExpired(it.value) }
    }

    private fun isExpired(entry: PendingOAuth): Boolean =
        entry.createdAt.isBefore(clock.instant().minus(TTL))

    private companion object {
        val TTL: Duration = Duration.ofMinutes(10)
    }
}
