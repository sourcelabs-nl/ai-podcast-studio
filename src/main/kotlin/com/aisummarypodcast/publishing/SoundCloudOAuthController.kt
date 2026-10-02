package com.aisummarypodcast.publishing

import com.aisummarypodcast.config.OAuthPkceHelper
import com.aisummarypodcast.user.UserService
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.util.HtmlUtils
import java.net.URLEncoder
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@RestController
class SoundCloudOAuthController(
    private val userService: UserService,
    private val soundCloudClient: SoundCloudClient,
    private val oauthConnectionService: OAuthConnectionService,
    private val credentialResolver: SoundCloudCredentialResolver,
    private val pkceHelper: OAuthPkceHelper
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val pendingVerifiers = ConcurrentHashMap<String, PendingOAuth>()

    @GetMapping("/users/{userId}/oauth/soundcloud/authorize")
    fun authorize(@PathVariable userId: String): ResponseEntity<Any> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()

        val credentials = try {
            credentialResolver.resolve(userId)
        } catch (_: IllegalStateException) {
            return ResponseEntity.status(503).body(mapOf("error" to "SoundCloud integration is not configured"))
        }

        cleanExpiredVerifiers()

        val codeVerifier = pkceHelper.generateCodeVerifier()
        val codeChallenge = pkceHelper.generateCodeChallenge(codeVerifier)
        val state = pkceHelper.createSignedState(userId)

        pendingVerifiers[state] = PendingOAuth(codeVerifier)

        val params = mapOf(
            "client_id" to credentials.clientId,
            "redirect_uri" to credentials.callbackUri,
            "response_type" to "code",
            "code_challenge" to codeChallenge,
            "code_challenge_method" to "S256",
            "state" to state,
            "scope" to "non-expiring"
        )
        val queryString = params.entries.joinToString("&") { (k, v) ->
            "$k=${URLEncoder.encode(v, Charsets.UTF_8)}"
        }
        val authorizationUrl = "https://secure.soundcloud.com/authorize?$queryString"

        return ResponseEntity.ok(AuthorizeResponse(authorizationUrl))
    }

    @GetMapping("/oauth/soundcloud/callback")
    fun callback(
        @RequestParam code: String,
        @RequestParam state: String,
        @RequestParam(required = false) error: String?
    ): ResponseEntity<String> {
        if (error != null) {
            return htmlResponse("SoundCloud authorization failed: $error")
        }

        val userId = pkceHelper.verifySignedState(state)
        if (userId == null) {
            return ResponseEntity.badRequest()
                .contentType(MediaType.TEXT_HTML)
                .body(htmlPage("Authorization Error", "Invalid or tampered authorization request."))
        }

        val pending = pendingVerifiers.remove(state)
        if (pending == null) {
            return ResponseEntity.badRequest()
                .contentType(MediaType.TEXT_HTML)
                .body(htmlPage("Authorization Expired", "The authorization request has expired. Please try again."))
        }

        return try {
            val credentials = credentialResolver.resolve(userId)
            val tokenResponse = soundCloudClient.exchangeCodeForTokens(
                code = code,
                clientId = credentials.clientId,
                clientSecret = credentials.clientSecret,
                redirectUri = credentials.callbackUri,
                codeVerifier = pending.codeVerifier
            )

            val expiresAt = tokenResponse.expiresIn?.let { Instant.now().plusSeconds(it) }

            oauthConnectionService.storeConnection(
                userId = userId,
                provider = "soundcloud",
                accessToken = tokenResponse.accessToken,
                refreshToken = tokenResponse.refreshToken,
                expiresAt = expiresAt,
                scopes = tokenResponse.scope
            )

            log.info("SoundCloud account connected for user {}", userId)
            htmlResponse("SoundCloud connected successfully! You can close this tab.")
        } catch (e: Exception) {
            log.error("Failed to exchange SoundCloud authorization code for user {}: {}", userId, e.message, e)
            htmlResponse("Failed to connect SoundCloud. Please try again.")
        }
    }

    @GetMapping("/users/{userId}/oauth/soundcloud/status")
    fun status(@PathVariable userId: String): ResponseEntity<Any> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        if (!credentialResolver.isConfigured(userId)) {
            return ResponseEntity.status(503).body(mapOf("error" to "SoundCloud integration is not configured"))
        }
        val status = oauthConnectionService.getStatus(userId, "soundcloud")
        if (!status.connected) {
            return ResponseEntity.ok(status)
        }

        val quota = try {
            val connection = oauthConnectionService.getConnection(userId, "soundcloud")!!
            val me = soundCloudClient.getMe(connection.accessToken)
            me.quota
        } catch (e: Exception) {
            log.warn("Failed to fetch SoundCloud quota for user {}: {}", userId, e.message)
            null
        }

        return ResponseEntity.ok(mapOf(
            "connected" to status.connected,
            "scopes" to status.scopes,
            "connectedAt" to status.connectedAt,
            "quota" to quota?.let {
                mapOf(
                    "unlimitedUploadQuota" to it.unlimitedUploadQuota,
                    "uploadSecondsUsed" to it.uploadSecondsUsed,
                    "uploadSecondsLeft" to it.uploadSecondsLeft
                )
            }
        ))
    }

    @DeleteMapping("/users/{userId}/oauth/soundcloud/tracks/{trackId}")
    fun deleteTrack(
        @PathVariable userId: String,
        @PathVariable trackId: Long
    ): ResponseEntity<Any> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        if (!credentialResolver.isConfigured(userId)) {
            return ResponseEntity.status(503).body(mapOf("error" to "SoundCloud integration is not configured"))
        }
        val connection = oauthConnectionService.getConnection(userId, "soundcloud")
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "No SoundCloud connection"))

        return try {
            soundCloudClient.deleteTrack(connection.accessToken, trackId)
            log.info("Deleted SoundCloud track {} for user {}", trackId, userId)
            ResponseEntity.ok(mapOf("deleted" to true, "trackId" to trackId))
        } catch (e: Exception) {
            log.error("Failed to delete SoundCloud track {} for user {}: {}", trackId, userId, e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to "Failed to delete track: ${e.message}"))
        }
    }

    @DeleteMapping("/users/{userId}/oauth/soundcloud")
    fun disconnect(@PathVariable userId: String): ResponseEntity<Any> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        if (!credentialResolver.isConfigured(userId)) {
            return ResponseEntity.status(503).body(mapOf("error" to "SoundCloud integration is not configured"))
        }
        return if (oauthConnectionService.deleteConnection(userId, "soundcloud")) {
            log.info("SoundCloud account disconnected for user {}", userId)
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.notFound().build()
        }
    }

    private fun cleanExpiredVerifiers() {
        val cutoff = Instant.now().minusSeconds(600) // 10 minute TTL
        pendingVerifiers.entries.removeIf { it.value.createdAt.isBefore(cutoff) }
    }

    private fun htmlResponse(message: String): ResponseEntity<String> =
        ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(htmlPage("SoundCloud", message))

    private fun htmlPage(title: String, message: String): String = """
        <!DOCTYPE html>
        <html><head><title>${HtmlUtils.htmlEscape(title)}</title></head>
        <body style="font-family:system-ui;display:flex;justify-content:center;align-items:center;height:100vh;margin:0">
        <p style="font-size:1.2em">${HtmlUtils.htmlEscape(message)}</p>
        </body></html>
    """.trimIndent()
}
