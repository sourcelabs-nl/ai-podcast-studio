package com.aisummarypodcast.llm

import com.openai.models.completions.CompletionUsage
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.model.ChatResponse

/**
 * Token counts for a single LLM call, plus the provider's own reported cost when it supplies one.
 *
 * OpenRouter returns `cost` (USD) inside its usage object; Spring AI exposes it through the native
 * usage's additional properties. Providers that report nothing (the direct `openai` provider) leave
 * [reportedCostUsd] null and are costed from the configured per-Mtok rates.
 */
data class TokenUsage(
    val inputTokens: Int,
    val outputTokens: Int,
    val reportedCostUsd: Double? = null,
    /** True when [reportedCostUsd] was replayed from the LLM cache rather than freshly charged. */
    val reportedCostFromCache: Boolean = false
) {
    companion object {
        private val log = LoggerFactory.getLogger(TokenUsage::class.java)

        /**
         * Response-metadata key under which [CachingChatModel] replays a cached call's reported
         * cost. The reconstructed response has no native usage object to carry it.
         */
        const val REPORTED_COST_METADATA_KEY = "reportedCostUsd"

        private const val NATIVE_COST_KEY = "cost"

        fun fromChatResponse(response: ChatResponse?): TokenUsage {
            if (response != null && response.metadata?.usage == null) {
                log.warn("LLM response missing usage metadata — reporting zero tokens")
            }
            val metadata = response?.metadata ?: return TokenUsage(0, 0)
            val usage = metadata.usage ?: return TokenUsage(0, 0)
            val cachedCost = validCost(metadata.get<Any>(REPORTED_COST_METADATA_KEY))
            return TokenUsage(
                inputTokens = usage.promptTokens ?: 0,
                outputTokens = usage.completionTokens ?: 0,
                reportedCostUsd = cachedCost ?: reportedCostFromNativeUsage(usage.nativeUsage),
                reportedCostFromCache = cachedCost != null
            )
        }

        /**
         * Reads the provider-reported cost from the native usage object. `_additionalProperties()`
         * is not a stable API surface, so any shape or conversion failure degrades to null and the
         * caller falls back to the configured rates rather than failing the pipeline.
         */
        private fun reportedCostFromNativeUsage(nativeUsage: Any?): Double? {
            if (nativeUsage !is CompletionUsage) return null
            return runCatching {
                nativeUsage._additionalProperties()[NATIVE_COST_KEY]?.asNumber()?.orElse(null)?.toDouble()
            }.onFailure {
                log.debug("Could not read provider-reported cost from native usage: {}", it.message)
            }.getOrNull()?.let(::validCost)
        }

        /** Absent, non-numeric, non-finite and negative values all mean "no reported cost". */
        private fun validCost(value: Any?): Double? {
            val cost = when (value) {
                is Double -> value
                is Number -> value.toDouble()
                else -> return null
            }
            return cost.takeIf { it.isFinite() && it >= 0.0 }
        }
    }
}
