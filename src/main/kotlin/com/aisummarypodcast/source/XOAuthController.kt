package com.aisummarypodcast.source

import com.aisummarypodcast.publishing.AuthorizeResponse
import com.aisummarypodcast.publishing.OAuthConnectionService
import com.aisummarypodcast.publishing.OAuthProviderPages
import com.aisummarypodcast.publishing.integrationNotConfigured
import com.aisummarypodcast.publishing.toResponse
import com.aisummarypodcast.user.UserService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
class XOAuthController(
    private val userService: UserService,
    private val oauthService: XOAuthService,
    private val oauthConnectionService: OAuthConnectionService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/users/{userId}/oauth/x/authorize")
    fun authorize(@PathVariable userId: String): ResponseEntity<Any> {
        if (!oauthService.isConfigured()) return integrationNotConfigured("X")
        userService.findById(userId) ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(AuthorizeResponse(oauthService.authorizationUrl(userId)))
    }

    @GetMapping("/oauth/x/callback")
    fun callback(
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) error: String?
    ): ResponseEntity<String> =
        oauthService.handleCallback(code, state, error).toResponse(OAuthProviderPages.X)

    @GetMapping("/users/{userId}/oauth/x/status")
    fun status(@PathVariable userId: String): ResponseEntity<Any> {
        if (!oauthService.isConfigured()) return integrationNotConfigured("X")
        userService.findById(userId) ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(oauthService.getStatus(userId))
    }

    @DeleteMapping("/users/{userId}/oauth/x")
    fun disconnect(@PathVariable userId: String): ResponseEntity<Any> {
        if (!oauthService.isConfigured()) return integrationNotConfigured("X")
        userService.findById(userId) ?: return ResponseEntity.notFound().build()

        return if (oauthConnectionService.deleteConnection(userId, "x")) {
            log.info("X account disconnected for user {}", userId)
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.notFound().build()
        }
    }
}
