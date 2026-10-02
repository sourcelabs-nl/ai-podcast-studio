package com.aisummarypodcast.publishing

import com.aisummarypodcast.api.InvalidRequestException
import com.aisummarypodcast.store.ApiKeyCategory
import com.aisummarypodcast.user.UserProviderConfigService
import com.aisummarypodcast.user.UserService
import tools.jackson.databind.ObjectMapper
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/users/{userId}/publishing/test")
class PublishingTestController(
    private val userService: UserService,
    private val publishingTestService: PublishingTestService,
    private val providerConfigService: UserProviderConfigService,
    private val objectMapper: ObjectMapper
) {

    @PostMapping("/ftp")
    fun testFtp(
        @PathVariable userId: String,
        @RequestBody credentials: FtpTestCredentials
    ): ResponseEntity<TestConnectionResult> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()

        val resolvedPassword = credentials.password?.takeIf { it.isNotBlank() }
            ?: resolveStoredFtpPassword(userId)
            ?: return ResponseEntity.ok(TestConnectionResult(success = false, message = "No password provided and no stored credentials found"))

        val resolved = credentials.copy(password = resolvedPassword)
        val result = publishingTestService.testFtp(resolved)
        return ResponseEntity.ok(result)
    }

    @PostMapping("/soundcloud")
    fun testSoundCloud(
        @PathVariable userId: String
    ): ResponseEntity<TestConnectionResult> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        val result = publishingTestService.testSoundCloud(userId)
        return ResponseEntity.ok(result)
    }

    @PostMapping("/{target}")
    fun testUnsupported(
        @PathVariable userId: String,
        @PathVariable target: String
    ): ResponseEntity<Any> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()
        throw InvalidRequestException("Unsupported target: $target")
    }

    private fun resolveStoredFtpPassword(userId: String): String? {
        val config = providerConfigService.resolveConfig(userId, ApiKeyCategory.PUBLISHING, "ftp") ?: return null
        val apiKey = config.apiKey ?: return null
        return try {
            @Suppress("UNCHECKED_CAST")
            val stored = objectMapper.readValue(apiKey, Map::class.java) as Map<String, Any>
            stored["password"] as? String
        } catch (_: Exception) {
            null
        }
    }
}
