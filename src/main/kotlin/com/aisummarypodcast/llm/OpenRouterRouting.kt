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
     * Model prefixes of closed-weight vendors, whose models are served only by the vendor itself or a
     * cloud reselling the vendor's own deployment (Azure, Bedrock). Those endpoints all report
     * quantization "unknown", so the floor would reject every one of them and the request would come
     * back 404 "No endpoints found": `openai/gpt-6-luna` has seven endpoints, all "unknown". No
     * third party can serve a lossy copy of a closed-weight model, so the floor protects nothing
     * there and is left out of the request. `google/` is deliberately absent: it also publishes
     * open-weight Gemma models that third parties quantize.
     */
    private val CLOSED_WEIGHT_PREFIXES = listOf("openai/", "anthropic/")

    private fun isClosedWeight(model: String?): Boolean =
        model != null && CLOSED_WEIGHT_PREFIXES.any { model.startsWith(it) }

    /**
     * Model prefixes whose models reject a `temperature`. OpenAI's reasoning models do not list it
     * among their supported parameters, so with `require_parameters` a request carrying one matches
     * no endpoint: `openai/gpt-6-luna` answered 404 "No endpoints found that can handle the requested
     * parameters" with `temperature: 0.9` and routed to OpenAI without it (probed 2026-09-23).
     */
    private val NO_TEMPERATURE_PREFIXES = listOf("openai/")

    /** Whether a request to [model] may carry a temperature; see [NO_TEMPERATURE_PREFIXES]. */
    fun acceptsTemperature(model: String): Boolean = NO_TEMPERATURE_PREFIXES.none { model.startsWith(it) }

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
     *
     * The quantization floor is sent for every [model] except a closed-weight vendor's (see
     * [CLOSED_WEIGHT_PREFIXES]); a call that names no model gets the floor.
     */
    fun extraBodyFor(
        provider: String,
        reasoningEffort: String?,
        preferences: ProviderPreferences = ProviderPreferences.DEFAULT,
        model: String? = null
    ): Map<String, Any> {
        if (provider != PROVIDER) return emptyMap()
        val routing = mutableMapOf<String, Any>()
        if (!isClosedWeight(model)) routing["quantizations"] = ACCEPTED_QUANTIZATIONS
        // Skip an endpoint that cannot honour what the request actually sends rather than letting it
        // silently ignore maxTokens or the reasoning budget.
        routing["require_parameters"] = true
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
    preferences: ProviderPreferences = ProviderPreferences.DEFAULT,
    model: String? = null
): OpenAiChatOptions.Builder {
    if (provider != OpenRouterRouting.PROVIDER) {
        return if (reasoningEffort != null) reasoningEffort(reasoningEffort) else this
    }
    return extraBody(OpenRouterRouting.extraBodyFor(provider, reasoningEffort, preferences, model))
}

/**
 * Routing and reasoning as the run resolved them for [model]'s stage. A temperature set earlier on
 * the builder is cleared for a model that rejects one (see [OpenRouterRouting.acceptsTemperature]),
 * since every stage sets one and such a model would otherwise match no endpoint.
 *
 * The stage's timeout is set here too: Spring AI 2.0.1 sends the options' timeout on every request,
 * and one left unset defaults to 60s, overriding the client's stage timeout and cutting a compose
 * request off after a minute.
 */
fun OpenAiChatOptions.Builder.withRoutingAndReasoning(model: ResolvedModel): OpenAiChatOptions.Builder {
    if (!OpenRouterRouting.acceptsTemperature(model.model)) temperature(null)
    model.requestTimeout?.let { timeout(it) }
    return withRoutingAndReasoning(model.provider, model.reasoningEffort, model.providerPreferences, model.model)
}
