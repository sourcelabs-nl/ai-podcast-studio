package com.aisummarypodcast.user

import com.aisummarypodcast.llm.openAiApiBaseUrl
import com.aisummarypodcast.publishing.TestConnectionResult
import com.aisummarypodcast.store.ApiKeyCategory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException

/**
 * Tests an LLM provider config exactly as the pipeline resolves it: the user's stored config, or the
 * environment fallback when none is stored. An empty key fails without a request, because that is
 * the state in which the pipeline itself fails with "At least one credential source must be specified".
 *
 * The request consumes no tokens. OpenRouter answers `/models` with 200 for any key, so it is
 * checked on `/key`, which answers 401 for an invalid one.
 */
@Service
class LlmProviderTestService(
    private val providerConfigService: UserProviderConfigService,
    private val restClientBuilder: RestClient.Builder
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun test(userId: String, provider: String): TestConnectionResult {
        val config = providerConfigService.resolveConfig(userId, ApiKeyCategory.LLM, provider)
            ?: return TestConnectionResult(false, "No config for '$provider' and no environment fallback")
        val apiKey = config.apiKey?.takeIf { it.isNotBlank() }
        if (apiKey == null && provider != KEYLESS_PROVIDER) {
            return TestConnectionResult(false, "The stored API key for '$provider' is empty")
        }

        val path = if (provider == OPENROUTER) "/key" else "/models"
        return try {
            restClientBuilder.clone().baseUrl(openAiApiBaseUrl(config.baseUrl)).build()
                .get()
                .uri(path)
                .headers { headers -> apiKey?.let { headers.setBearerAuth(it) } }
                .retrieve()
                .toBodilessEntity()
            TestConnectionResult(true, "Connected to '$provider'")
        } catch (e: RestClientResponseException) {
            log.info("[Settings] LLM provider test for '{}' failed with HTTP {}", provider, e.statusCode.value())
            TestConnectionResult(false, "'$provider' answered HTTP ${e.statusCode.value()}")
        } catch (e: RestClientException) {
            log.info("[Settings] LLM provider test for '{}' failed: {}", provider, e.message)
            TestConnectionResult(false, "Could not reach '$provider': ${e.message}")
        }
    }

    private companion object {
        const val OPENROUTER = "openrouter"
        const val KEYLESS_PROVIDER = "ollama"
    }
}
