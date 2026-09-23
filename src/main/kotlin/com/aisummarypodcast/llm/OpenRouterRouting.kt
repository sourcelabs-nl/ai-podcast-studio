package com.aisummarypodcast.llm

import org.springframework.ai.openai.OpenAiChatOptions

/**
 * The extra request body for a call routed through OpenRouter: the provider quality floor, and the
 * reasoning budget.
 *
 * **Provider floor.** Without one OpenRouter picks from every endpoint serving the model, and those
 * differ far more than the shared model name suggests: `z-ai/glm-5.3` is served by 25 endpoints
 * whose 30-minute uptime ranges from 87.6% to 100% and whose quantization ranges from fp4 to bf16,
 * nine of them fp4 or unknown. A lossy endpoint follows instructions worse, which is how one
 * compose response came back with no speaker tags at all, another without the `finish_reason` the
 * client requires, and a third with the wrong podcast name in the introduction.
 *
 * The floor is a quality bar rather than an allowlist of names, because the provider mix changes:
 * the configuration used to claim Z.AI was the only provider for GLM-5.3, and by the time that
 * mattered there were 25. Enough endpoints clear the bar to stay redundant — 9 for the compose
 * model and 11 for the filter and dedup model — and `allow_fallbacks` is left at its default, so
 * one endpoint going down still falls through to another that also clears it.
 *
 * **Reasoning.** OpenRouter takes reasoning as a `reasoning` object and, per its documentation,
 * does not accept OpenAI's flat `reasoning_effort` as an alias. Spring AI's
 * `OpenAiChatOptions.reasoningEffort` serializes to exactly that flat field, so setting it had no
 * effect on an OpenRouter call: the effort was silently ignored and the routed provider went on
 * deciding for itself. That is measurable in the bill — compose output ranged from 6,048 to 72,821
 * tokens for scripts of comparable length — and it is why the effort now travels in the extra body
 * instead. Sending the flat field here as well would be worse than useless: combined with
 * `require_parameters` it is an unsupported parameter and could steer routing on a field the
 * provider never reads.
 *
 * Every effort is stated, including "none". See [extraBodyFor] for why omitting the block is not the
 * same as asking for no reasoning, and what it cost.
 */
object OpenRouterRouting {

    const val PROVIDER = "openrouter"

    /** No reasoning wanted. Sent as a real `effort: "none"`; see [extraBodyFor]. */
    const val NO_REASONING = "none"

    /** Anything below fp8 loses too much precision to follow a long, format-sensitive prompt. */
    private val ACCEPTED_QUANTIZATIONS = listOf("fp8", "fp16", "bf16", "fp32")

    /**
     * Extra body for a request to [provider], or an empty map when the provider is not OpenRouter —
     * neither block means anything to the direct `openai` provider.
     *
     * A `reasoning` block is sent whenever an effort is given, [NO_REASONING] included. Omitting the
     * block does not mean "no reasoning": OpenRouter treats an absent parameter as inferred from the
     * model's own default, and the dedup and filter model reports `default_enabled: true` at
     * `default_effort: "high"`. Measured against the live API on that model, omitting the block cost
     * 47 reasoning tokens on a one-line task, `effort: "low"` cost 22, and `effort: "none"` cost
     * none. Sending nothing therefore bought high-effort reasoning, whose tokens are charged against
     * `maxTokens`; on a real dedup prompt that consumed the entire output budget and returned empty
     * content, failing episode 200 five times over.
     *
     * `effort: "none"` is not universally available: an endpoint that reports `mandatory: true`
     * rejects it outright, as `z-ai/glm-5.3` does with *"Reasoning is mandatory for this endpoint and
     * cannot be disabled"* (HTTP 400). That is deliberately left to surface. Configuring an effort
     * the resolved model cannot honour is a configuration error, and a loud 400 says so, where
     * silently sending nothing bought the model's high-effort default instead.
     *
     * `exclude` keeps the reasoning text out of the response. The tokens are billed either way, and
     * the client cannot read it regardless: reasoning comes back in `message.reasoning`, which
     * `openai-java`'s `ChatCompletionMessage` has no field for. Leaving it out of the response also
     * means it cannot be mistaken for the script.
     *
     * [preferences] adds OpenRouter's `provider.sort` (`price`, `throughput` or `latency`) and
     * `provider.preferred_min_throughput` (tokens per second) when a run sets them.
     */
    fun extraBodyFor(
        provider: String,
        reasoningEffort: String?,
        preferences: ProviderPreferences = ProviderPreferences.DEFAULT
    ): Map<String, Any> {
        if (provider != PROVIDER) return emptyMap()
        val routing = mutableMapOf<String, Any>(
            "quantizations" to ACCEPTED_QUANTIZATIONS,
            // Skip an endpoint that cannot honour what the request actually sends rather than
            // letting it silently ignore maxTokens or the reasoning budget.
            "require_parameters" to true,
        )
        // Sorting and the throughput preference choose among the endpoints that clear the floor;
        // they never widen it. Both are sent only when a run sets them.
        preferences.sort?.let { routing["sort"] = it }
        preferences.preferredMinThroughput?.let { routing["preferred_min_throughput"] = it }
        val body = mutableMapOf<String, Any>("provider" to routing)
        if (reasoningEffort != null) {
            body["reasoning"] = mapOf(
                "effort" to reasoningEffort,
                "exclude" to true,
            )
        }
        return body
    }
}

/**
 * Applies the routing floor and the reasoning budget in the form the resolved provider actually
 * reads: the extra body for OpenRouter, and the flat `reasoning_effort` field for a direct OpenAI
 * call, where that field is the real one.
 */
fun OpenAiChatOptions.Builder.withRoutingAndReasoning(
    provider: String,
    reasoningEffort: String?,
    preferences: ProviderPreferences = ProviderPreferences.DEFAULT
): OpenAiChatOptions.Builder {
    if (provider != OpenRouterRouting.PROVIDER) {
        return if (reasoningEffort != null) reasoningEffort(reasoningEffort) else this
    }
    return extraBody(OpenRouterRouting.extraBodyFor(provider, reasoningEffort, preferences))
}

/** Routing and reasoning as the run resolved them for [model]'s stage. */
fun OpenAiChatOptions.Builder.withRoutingAndReasoning(model: ResolvedModel): OpenAiChatOptions.Builder =
    withRoutingAndReasoning(model.provider, model.reasoningEffort, model.providerPreferences)
