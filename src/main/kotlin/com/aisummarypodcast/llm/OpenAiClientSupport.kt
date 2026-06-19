package com.aisummarypodcast.llm

import com.aisummarypodcast.user.ProviderConfig
import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import java.time.Duration

/**
 * Spring AI 2.0 builds its OpenAI models on the official OpenAI Java SDK, whose base URL must point
 * at the API root including the version segment (the SDK default is https://api.openai.com/v1).
 *
 * Stored provider base URLs omit it (e.g. "https://openrouter.ai/api", "https://api.openai.com")
 * because the previous Spring AI client appended "/v1/chat/completions" itself. Append "/v1" when
 * it is missing so OpenRouter ("/api" -> "/api/v1"), OpenAI ("" -> "/v1") and Ollama (already
 * "/v1") all resolve to a valid endpoint.
 */
internal fun openAiApiBaseUrl(baseUrl: String): String {
    val trimmed = baseUrl.trimEnd('/')
    return if (trimmed.endsWith("/v1")) trimmed else "$trimmed/v1"
}

/** Builds an OpenAI SDK client from a stored [ProviderConfig], normalizing the base URL. */
internal fun buildOpenAiClient(config: ProviderConfig, timeout: Duration): OpenAIClient =
    OpenAIOkHttpClient.builder()
        .apiKey(config.apiKey ?: "")
        .baseUrl(openAiApiBaseUrl(config.baseUrl))
        .timeout(timeout)
        .build()
