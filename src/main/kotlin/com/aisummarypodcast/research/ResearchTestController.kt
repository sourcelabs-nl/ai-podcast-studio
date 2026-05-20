package com.aisummarypodcast.research

import com.aisummarypodcast.store.ApiKeyCategory
import com.aisummarypodcast.user.UserProviderConfigService
import com.aisummarypodcast.user.UserService
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClient

data class ResearchTestResult(
    val success: Boolean,
    val message: String,
    val resultCount: Int? = null
)

@RestController
@RequestMapping("/users/{userId}/research/test")
class ResearchTestController(
    private val userService: UserService,
    private val providerConfigService: UserProviderConfigService,
    private val restClientBuilder: RestClient.Builder
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/tavily")
    fun testTavily(@PathVariable userId: String): ResponseEntity<ResearchTestResult> {
        userService.findById(userId) ?: return ResponseEntity.notFound().build()

        val config = providerConfigService.resolveConfig(userId, ApiKeyCategory.RESEARCH, "tavily")
        val apiKey = config?.apiKey
        if (apiKey.isNullOrBlank()) {
            return ResponseEntity.ok(
                ResearchTestResult(
                    success = false,
                    message = "No Tavily API key configured (neither user-stored key nor TAVILY_API_KEY env var)."
                )
            )
        }

        return try {
            val client = restClientBuilder.baseUrl(config.baseUrl).build()
            val response = client.post()
                .uri("/search")
                .header("Authorization", "Bearer $apiKey")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("query" to "AI podcast deep dive test", "max_results" to 1, "search_depth" to "basic"))
                .retrieve()
                .body(Map::class.java)

            @Suppress("UNCHECKED_CAST")
            val results = (response?.get("results") as? List<Map<String, Any?>>).orEmpty()
            ResponseEntity.ok(
                ResearchTestResult(
                    success = true,
                    message = "Tavily reachable. Returned ${results.size} result(s).",
                    resultCount = results.size
                )
            )
        } catch (e: Exception) {
            log.warn("[Research] Tavily test failed for user '{}': {}", userId, e.message)
            ResponseEntity.ok(
                ResearchTestResult(
                    success = false,
                    message = "Tavily request failed: ${e.message ?: e.javaClass.simpleName}"
                )
            )
        }
    }
}
