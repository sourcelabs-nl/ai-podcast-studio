package com.aisummarypodcast.source

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.OAuthPkceHelper
import com.aisummarypodcast.publishing.AuthorizeResponse
import com.aisummarypodcast.publishing.OAuthConnectionService
import com.aisummarypodcast.publishing.PendingOAuth
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
class XOAuthController(
    private val appProperties: AppProperties,
    private val userService: UserService,
    private val xClient: XClient,
    private val oauthConnectionService: OAuthConnectionService,
    private val pkceHelper: OAuthPkceHelper
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val pendingVerifiers = ConcurrentHashMap<String, PendingOAuth>()

    private fun isConfigured(): Boolean =
        !appProperties.x.clientId.isNullOrBlank() &&
            !appProperties.x.clientSecret.isNullOrBlank()

    private fun redirectUri(): String =
        "${appProperties.feed.baseUrl}/oauth/x/callback"

    @GetMapping("/users/{userId}/oauth/x/authorize")
    fun authorize(@PathVariable userId: String): ResponseEntity<Any> {
        if (!isConfigured()) {
            return ResponseEntity.status(503).body(mapOf("error" to "X integration is not configured"))
        }
        userService.findById(userId) ?: return ResponseEntity.notFound().build()

        cleanExpiredVerifiers()

        val codeVerifier = pkceHelper.generateCodeVerifier()
        val codeChallenge = pkceHelper.generateCodeChallenge(codeVerifier)
        val state = pkceHelper.createSignedState(userId)

        pendingVerifiers[state] = PendingOAuth(codeVerifier)

        val params = mapOf(
            "client_id" to appProperties.x.clientId!!,
            "redirect_uri" to redirectUri(),
            "response_type" to "code",
            "code_challenge" to codeChallenge,
            "code_challenge_method" to "S256",
            "state" to state,
            "scope" to "tweet.read users.read offline.access"
        )
        val queryString = params.entries.joinToString("&") { (k, v) ->
            "$k=${URLEncoder.encode(v, Charsets.UTF_8)}"
        }
        val authorizationUrl = "https://twitter.com/i/oauth2/authorize?$queryString"

        return ResponseEntity.ok(AuthorizeResponse(authorizationUrl))
    }

    @GetMapping("/oauth/x/callback")
    fun callback(
        @RequestParam code: String,
        @RequestParam state: String,
        @RequestParam(required = false) error: String?
    ): ResponseEntity<String> {
        if (error != null) {
            return htmlResponse("X authorization failed: $error")
        }

        val userId = pkceHelper.verifySignedState(state)
        if (userId == null) {
            return ResponseEntity.badRequest()
                .contentType(MediaType.TEXT_HTML)
                .body(htmlPage("Authorization Error", "Invalid or tampered authorization request."))
        }

        val pending = pendingVerifiers.remove(state)
        if (pending == null) {
            return ResponseEntity.status(404)
                .contentType(MediaType.TEXT_HTML)
                .body(htmlPage("Authorization Expired", "The authorization request has expired. Please try again."))
        }

        return try {
            val tokenResponse = xClient.exchangeCodeForTokens(
                code = code,
                clientId = appProperties.x.clientId!!,
                clientSecret = appProperties.x.clientSecret!!,
                redirectUri = redirectUri(),
                codeVerifier = pending.codeVerifier
            )

            val expiresAt = tokenResponse.expiresIn?.let { Instant.now().plusSeconds(it) }

            oauthConnectionService.storeConnection(
                userId = userId,
                provider = "x",
                accessToken = tokenResponse.accessToken,
                refreshToken = tokenResponse.refreshToken,
                expiresAt = expiresAt,
                scopes = tokenResponse.scope
            )

            log.info("X account connected for user {}", userId)
            htmlResponse("X account connected successfully! You can close this tab.")
        } catch (e: Exception) {
            log.error("Failed to exchange X authorization code for user {}: {}", userId, e.message, e)
            htmlResponse("Failed to connect X account. Please try again.")
        }
    }

    @GetMapping("/users/{userId}/oauth/x/status")
    fun status(@PathVariable userId: String): ResponseEntity<Any> {
        if (!isConfigured()) {
            return ResponseEntity.status(503).body(mapOf("error" to "X integration is not configured"))
        }
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(oauthConnectionService.getStatus(userId, "x"))
    }

    @DeleteMapping("/users/{userId}/oauth/x")
    fun disconnect(@PathVariable userId: String): ResponseEntity<Any> {
        if (!isConfigured()) {
            return ResponseEntity.status(503).body(mapOf("error" to "X integration is not configured"))
        }
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        return if (oauthConnectionService.deleteConnection(userId, "x")) {
            log.info("X account disconnected for user {}", userId)
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
            .body(htmlPage("X", message))

    private fun htmlPage(title: String, message: String): String = """
        <!DOCTYPE html>
        <html><head><title>${HtmlUtils.htmlEscape(title)}</title></head>
        <body style="font-family:system-ui;display:flex;justify-content:center;align-items:center;height:100vh;margin:0">
        <p style="font-size:1.2em">${HtmlUtils.htmlEscape(message)}</p>
        </body></html>
    """.trimIndent()
}
