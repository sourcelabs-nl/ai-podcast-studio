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
 */
object OpenRouterRouting {

    const val PROVIDER = "openrouter"

    /** No reasoning wanted; see [extraBodyFor] for why this sends no `reasoning` block at all. */
    const val NO_REASONING = "none"

    /** Anything below fp8 loses too much precision to follow a long, format-sensitive prompt. */
    private val ACCEPTED_QUANTIZATIONS = listOf("fp8", "fp16", "bf16", "fp32")

    /**
     * Extra body for a request to [provider], or an empty map when the provider is not OpenRouter —
     * neither block means anything to the direct `openai` provider.
     *
     * A `reasoning` block is sent only when reasoning is actually wanted. `require_parameters`
     * restricts routing to endpoints that support every parameter supplied, so asking a
     * deliberately non-reasoning model (the filter and dedup stages run on `deepseek-v4-flash`) to
     * acknowledge a reasoning parameter risks leaving no eligible endpoint at all. Those stages get
     * their non-reasoning behaviour from the model they run on, not from a flag.
     *
     * `exclude` keeps the reasoning text out of the response. The tokens are billed either way, and
     * the client cannot read it regardless: reasoning comes back in `message.reasoning`, which
     * `openai-java`'s `ChatCompletionMessage` has no field for. Leaving it out of the response also
     * means it cannot be mistaken for the script.
     */
    fun extraBodyFor(provider: String, reasoningEffort: String?): Map<String, Any> {
        if (provider != PROVIDER) return emptyMap()
        val body = mutableMapOf<String, Any>(
            "provider" to mapOf(
                "quantizations" to ACCEPTED_QUANTIZATIONS,
                // Skip an endpoint that cannot honour what the request actually sends rather than
                // letting it silently ignore maxTokens or the reasoning budget.
                "require_parameters" to true,
            )
        )
        if (reasoningEffort != null && reasoningEffort != NO_REASONING) {
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
    reasoningEffort: String?
): OpenAiChatOptions.Builder {
    if (provider != OpenRouterRouting.PROVIDER) {
        return if (reasoningEffort != null) reasoningEffort(reasoningEffort) else this
    }
    return extraBody(OpenRouterRouting.extraBodyFor(provider, reasoningEffort))
}
