package com.aisummarypodcast.publishing

import com.aisummarypodcast.user.UserService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
class SoundCloudOAuthController(
    private val userService: UserService,
    private val oauthService: SoundCloudOAuthService,
    private val oauthConnectionService: OAuthConnectionService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/users/{userId}/oauth/soundcloud/authorize")
    fun authorize(@PathVariable userId: String): ResponseEntity<Any> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        if (!oauthService.isConfigured(userId)) return integrationNotConfigured("SoundCloud")

        return ResponseEntity.ok(AuthorizeResponse(oauthService.authorizationUrl(userId)))
    }

    @GetMapping("/oauth/soundcloud/callback")
    fun callback(
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) error: String?
    ): ResponseEntity<String> =
        oauthService.handleCallback(code, state, error).toResponse(OAuthProviderPages.SOUNDCLOUD)

    @GetMapping("/users/{userId}/oauth/soundcloud/status")
    fun status(@PathVariable userId: String): ResponseEntity<Any> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        if (!oauthService.isConfigured(userId)) return integrationNotConfigured("SoundCloud")

        return ResponseEntity.ok(oauthService.getStatus(userId).toResponse())
    }

    @DeleteMapping("/users/{userId}/oauth/soundcloud/tracks/{trackId}")
    fun deleteTrack(
        @PathVariable userId: String,
        @PathVariable trackId: Long
    ): ResponseEntity<Any> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        if (!oauthService.isConfigured(userId)) return integrationNotConfigured("SoundCloud")

        return try {
            if (!oauthService.deleteTrack(userId, trackId)) {
                return ResponseEntity.badRequest().body(mapOf("error" to "No SoundCloud connection"))
            }
            ResponseEntity.ok(mapOf("deleted" to true, "trackId" to trackId))
        } catch (e: Exception) {
            log.error("Failed to delete SoundCloud track {} for user {}: {}", trackId, userId, e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to "Failed to delete track: ${e.message}"))
        }
    }

    @DeleteMapping("/users/{userId}/oauth/soundcloud")
    fun disconnect(@PathVariable userId: String): ResponseEntity<Any> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        if (!oauthService.isConfigured(userId)) return integrationNotConfigured("SoundCloud")

        return if (oauthConnectionService.deleteConnection(userId, "soundcloud")) {
            log.info("SoundCloud account disconnected for user {}", userId)
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.notFound().build()
        }
    }
}
