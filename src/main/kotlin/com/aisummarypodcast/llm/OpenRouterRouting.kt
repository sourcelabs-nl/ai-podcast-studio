package com.aisummarypodcast.llm

import org.springframework.ai.openai.OpenAiChatOptions

/**
 * OpenRouter's provider-routing block, sent as extra body on every request that goes through it.
 *
 * Without a floor OpenRouter picks from every endpoint serving the model, and those differ far more
 * than the shared model name suggests: `z-ai/glm-5.3` is served by 25 endpoints whose 30-minute
 * uptime ranges from 87.6% to 100% and whose quantization ranges from fp4 to bf16, nine of them fp4
 * or unknown. A lossy endpoint follows instructions worse, which is how one compose response came
 * back with no speaker tags at all and another without the `finish_reason` the client requires.
 *
 * The floor is a quality bar rather than an allowlist of names, because the provider mix changes:
 * the configuration used to claim Z.AI was the only provider for GLM-5.3, and by the time that
 * mattered there were 25. Enough endpoints clear the bar to stay redundant — 9 for the compose
 * model and 11 for the filter and dedup model — and `allow_fallbacks` is left at its default, so
 * one endpoint going down still falls through to another that also clears it.
 */
object OpenRouterRouting {

    const val PROVIDER = "openrouter"

    /** Anything below fp8 loses too much precision to follow a long, format-sensitive prompt. */
    private val ACCEPTED_QUANTIZATIONS = listOf("fp8", "fp16", "bf16", "fp32")

    /**
     * Extra body for a request to [provider], or an empty map when the provider is not OpenRouter —
     * a `provider` block is meaningless to the direct `openai` provider and is not sent there.
     */
    fun extraBodyFor(provider: String): Map<String, Any> =
        if (provider != PROVIDER) emptyMap()
        else mapOf(
            "provider" to mapOf(
                "quantizations" to ACCEPTED_QUANTIZATIONS,
                // Skip an endpoint that cannot honour what the request actually sends rather than
                // letting it silently ignore maxTokens or the reasoning effort.
                "require_parameters" to true,
            )
        )
}

/** Applies the routing floor when the model came from OpenRouter, and nothing otherwise. */
fun OpenAiChatOptions.Builder.withOpenRouterFloor(provider: String): OpenAiChatOptions.Builder {
    val extraBody = OpenRouterRouting.extraBodyFor(provider)
    return if (extraBody.isEmpty()) this else extraBody(extraBody)
}
