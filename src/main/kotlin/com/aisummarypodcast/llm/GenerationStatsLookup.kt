package com.aisummarypodcast.llm

/**
 * What it takes to ask OpenRouter about a request after it returned: the provider base URL and the
 * API key the request itself was made with. Keys are per user and never stored with the telemetry,
 * so the lookup carries the key of the call it belongs to.
 */
data class GenerationStatsLookup(val baseUrl: String, val apiKey: String) {
    override fun toString(): String = "GenerationStatsLookup(baseUrl=$baseUrl)"
}
