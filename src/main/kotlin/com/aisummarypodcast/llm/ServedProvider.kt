package com.aisummarypodcast.llm

import org.springframework.ai.chat.model.ChatResponse

/** The response-metadata key under which OpenRouter's `provider` field arrives. */
private const val SERVED_PROVIDER_METADATA_KEY = "provider"

/**
 * The upstream provider that served [response], as OpenRouter reports it in the completion's
 * top-level `provider` field (for example "Google" or "Anthropic"). Spring AI copies the
 * completion's unknown top-level fields into the response metadata, which is where it is read.
 *
 * Null when the response does not carry it: a direct OpenAI call, a cache hit (its reconstructed
 * response has no such key), or a value of an unexpected shape.
 */
fun servedProviderOf(response: ChatResponse?): String? =
    runCatching { response?.metadata?.get<Any>(SERVED_PROVIDER_METADATA_KEY) as? String }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
