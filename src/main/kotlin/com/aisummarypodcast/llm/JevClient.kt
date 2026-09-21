package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.store.ApiKeyCategory
import com.aisummarypodcast.user.UserProviderConfigService
import com.fasterxml.jackson.annotation.JsonProperty
import io.github.resilience4j.retry.RetryRegistry
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration

/** The OpenRouter provider whose credential reaches Jev. Jev is served through OpenRouter only. */
private const val JEV_CREDENTIAL_PROVIDER = "openrouter"

/** TypeSafe's own overload status, outside the 5xx range and not in [HttpStatusCode]'s vocabulary. */
private const val SYSTEM_OVERLOADED = 529

/** Which Jev deployment to ask. */
data class JevEndpoint(val url: String, val model: String)

/**
 * One `noul` question: a proposition about the shared state, answered as a probability in `[0,1]`.
 *
 * Only `noul` is modelled because only `noul` is used. Jev also answers `score` and `choice`, both
 * of which additionally require a `criteria` field whose shape differs between them, and neither
 * would be exercised by anything here. See `knowledge/references/jev-decisions-endpoint.md`.
 */
data class JevNoulQuestion(val instructions: String) {
    val type: String = "noul"
}

private data class JevRequest(
    val model: String,
    val state: Any,
    val questions: Map<String, JevNoulQuestion>
)

private data class JevApiResponse(
    val answers: Map<String, JevApiAnswer> = emptyMap(),
    val usage: JevApiUsage? = null
)

private data class JevApiAnswer(val noul: Double? = null)

private data class JevApiUsage(
    @param:JsonProperty("input_tokens") val inputTokens: Int? = null,
    val cost: Double? = null
)

/**
 * Answers to one batch of questions, keyed as the questions were.
 *
 * [reportedCostUsd] stays null when the call did not happen or failed, so an unanswered batch is
 * never mistaken for a free one. Nothing about a Jev call passes through [CachingChatModel] or
 * [CostEstimator], so a cost not returned here is a cost the pipeline's reporting cannot see.
 */
data class JevAnswers(
    val noul: Map<String, Double> = emptyMap(),
    val inputTokens: Int = 0,
    val reportedCostUsd: Double? = null
) {
    companion object {
        val NONE = JevAnswers()
    }
}

/**
 * Client for TypeSafe's Jev model on OpenRouter's decisions endpoint.
 *
 * Jev is not a chat model: it is absent from OpenRouter's `/api/v1/models` listing, is not served
 * by `/api/v1/chat/completions`, and returns constrained answers rather than text. No Spring AI
 * `ChatModel` can reach it, so this is a plain [RestClient] in the shape of
 * `com.aisummarypodcast.research.TavilyClient`.
 *
 * **Nothing here throws.** Every failure returns [JevAnswers.NONE] with a logged warning, and
 * callers are required to have a defined behaviour without an answer. That is not defensive habit:
 * the endpoint is alpha, served by a single provider with no OpenRouter routing fallback behind
 * it. In one morning it answered three consecutive requests with `503 no healthy upstream` and
 * both chunks of a live pipeline run with `529 system_overloaded`, while serving healthy requests
 * in under a second either side of both.
 *
 * A transient status is therefore retried before giving up, which is the only fallback this
 * endpoint has. Exhausting the attempts is not an error, just another way to get no answers.
 */
@Component
class JevClient(
    private val providerConfigService: UserProviderConfigService,
    private val restClientBuilder: RestClient.Builder,
    retryRegistry: RetryRegistry,
    appProperties: AppProperties
) {

    private val retry = retryRegistry.retry("jev-decisions")

    private val log = LoggerFactory.getLogger(javaClass)

    init {
        // Configured once on this client's own builder rather than per call, so a test can bind a
        // MockRestServiceServer factory afterwards and have it survive. A decision call returns in
        // about a second, so the bound is a wall against a hung endpoint rather than a budget any
        // healthy call approaches.
        restClientBuilder.requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(Duration.ofSeconds(10))
                setReadTimeout(appProperties.llm.dedup.gate.timeout)
            }
        )
    }

    /**
     * Asks every question in [questions] about the one shared [state].
     *
     * The batching is the point. Jev's per-call price is low because the endpoint expects shared
     * context to be shared: asking the same 60 questions one call per candidate re-sent the shared
     * state 60 times and cost $0.00605, against $0.00074 for all 60 in one request.
     *
     * The caller is responsible for keeping the request small enough. The endpoint rejects an
     * oversized one with `max_tokens_exceeded`, which arrives here as an ordinary failure and
     * yields no answers.
     */
    fun ask(
        userId: String,
        state: Any,
        questions: Map<String, JevNoulQuestion>,
        endpoint: JevEndpoint
    ): JevAnswers {
        if (questions.isEmpty()) return JevAnswers.NONE

        val apiKey = providerConfigService
            .resolveConfig(userId, ApiKeyCategory.LLM, JEV_CREDENTIAL_PROVIDER)
            ?.apiKey
        if (apiKey.isNullOrBlank()) {
            log.warn("[Jev] No '{}' API key configured (user '{}'), asking nothing", JEV_CREDENTIAL_PROVIDER, userId)
            return JevAnswers.NONE
        }

        return try {
            // The retry sits inside the catch, so exhausting it is just another way to get no
            // answers. Only a JevTransientException is retried; see JevTransientException.
            val response = retry.executeCallable {
                restClientBuilder.build()
                    .post()
                    .uri(endpoint.url)
                    .header("Authorization", "Bearer $apiKey")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(JevRequest(model = endpoint.model, state = state, questions = questions))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError) { _, response ->
                        throwForStatus(response.statusCode, String(response.body.readAllBytes()))
                    }
                    .body(JevApiResponse::class.java)
            } ?: return JevAnswers.NONE

            JevAnswers(
                // An answer missing its value is dropped rather than defaulted: the caller treats
                // an unanswered question as "no", and inventing a 0.0 here would say the same thing
                // while looking like the model's own judgement.
                noul = response.answers.mapNotNull { (key, answer) ->
                    answer.noul?.let { key to it }
                }.toMap(),
                inputTokens = response.usage?.inputTokens ?: 0,
                reportedCostUsd = response.usage?.cost
            )
        } catch (e: Exception) {
            log.warn("[Jev] Decision call for {} question(s) failed: {}", questions.size, e.message)
            JevAnswers.NONE
        }
    }

    /**
     * Raises the exception matching [status], so the retry can tell a fault worth repeating from
     * one that would fail the same way every time.
     *
     * The body is carried into the message because the endpoint puts the reason there rather than
     * in the status: an oversized batch and a bad request are both a bare 400, separated only by
     * the `error_type` in the body.
     */
    private fun throwForStatus(status: HttpStatusCode, body: String): Nothing {
        val code = status.value()
        if (code == SYSTEM_OVERLOADED || code == 429 || status.is5xxServerError) {
            throw JevTransientException("Jev decisions endpoint returned HTTP $code: $body")
        }
        throw IllegalStateException("Jev decisions endpoint rejected the request (HTTP $code): $body")
    }
}
