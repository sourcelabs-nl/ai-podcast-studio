package com.aisummarypodcast.llm

import com.aisummarypodcast.store.LlmCache
import com.aisummarypodcast.store.LlmCacheRepository
import com.aisummarypodcast.util.isConstraintViolation
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.MessageType
import org.springframework.ai.chat.metadata.ChatResponseMetadata
import org.springframework.ai.chat.metadata.DefaultUsage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.chat.prompt.Prompt
import reactor.core.publisher.Flux
import java.security.MessageDigest
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Wraps the underlying [ChatModel] with a SQLite-backed cache. Safe under Spring AI
 * tool-call loops: the cache key derives only from `USER` and `SYSTEM` messages, so identical
 * compose prompts hash to the same key regardless of how the tool loop unfolds. Cache lookup
 * only fires on the initial call (no `ASSISTANT`/`TOOL` messages yet); the cached value is
 * the first response in the loop that contains no pending tool calls (i.e. the final
 * assistant turn).
 *
 * [cacheEnabled] is false for an evaluation run, which neither reads nor writes the cache. The key
 * ignores temperature, so without a bypass k repetitions of one prompt variant would be a single
 * model call and k-1 replays of its answer, and the spread they were run to measure would be zero
 * by construction. Not writing either keeps an evaluation from displacing what production reads.
 *
 * [attribution] is what every request from this instance is issued for. A fresh instance is built
 * per call site, so it is per-generation without being shared, and it reaches the recorded row
 * without depending on which thread issues the request. The scoring stage builds one per article,
 * so each of its requests names the article it scored.
 */
class CachingChatModel(
    private val delegate: ChatModel,
    private val llmCacheRepository: LlmCacheRepository,
    private val resolvedModel: ResolvedModel,
    private val llmCallLogService: LlmCallLogService,
    private val cacheEnabled: Boolean = true,
    private val attribution: LlmCallAttribution = LlmCallAttribution.NONE
) : ChatModel {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun call(prompt: Prompt): ChatResponse {
        val model = prompt.options?.model ?: "default"
        val promptHash = sha256("$model:${userPromptText(prompt)}")
        val initialCall = isInitialCall(prompt) && cacheEnabled

        if (initialCall) {
            val cached = llmCacheRepository.findByPromptHashAndModel(promptHash, model)
            if (cached != null) {
                log.debug("LLM cache hit for model={} hash={}", model, promptHash.take(12))
                val response = reconstructResponse(cached)
                recordCall(Instant.now(), Duration.ZERO, response, cacheHit = true)
                return response
            }
        }

        val startedAt = Instant.now()
        val mark = TimeSource.Monotonic.markNow()
        val response = try {
            delegate.call(prompt)
        } catch (e: Exception) {
            recordFailedCall(startedAt, mark.elapsedNow(), e)
            throw e
        }
        recordCall(startedAt, mark.elapsedNow(), response, cacheHit = false)

        if (cacheEnabled && !hasPendingToolCalls(response)) {
            val responseText = response.result?.output?.text
            // Never cache a blank/empty completion. A model that degenerates (e.g. burns its whole
            // maxTokens budget producing nothing parseable) returns an empty string, not null —
            // caching it poisons every retry and every future regenerate with the same parse
            // failure, turning a transient model hiccup into a permanently stuck episode.
            if (!responseText.isNullOrBlank()) {
                val usage = response.metadata?.usage
                store(
                    LlmCache(
                        promptHash = promptHash,
                        model = model,
                        response = responseText,
                        createdAt = Instant.now().toString(),
                        inputTokens = usage?.promptTokens?.toInt(),
                        outputTokens = usage?.completionTokens?.toInt(),
                        reportedCostUsd = TokenUsage.fromChatResponse(response).reportedCostUsd
                    )
                )
            }
        }

        return response
    }

    override fun stream(prompt: Prompt): Flux<ChatResponse> = delegate.stream(prompt)

    override fun getDefaultOptions(): ChatOptions = delegate.defaultOptions

    // ChatClient's option merging (DefaultChatClientUtils) bases the request options on
    // chatModel.getOptions() — NOT getDefaultOptions(). If this returns the generic ChatModel
    // default (a DefaultChatOptions), the merged prompt options stay DefaultChatOptions and
    // OpenAiChatModel fails to cast them to OpenAiChatOptions. Delegate so the provider type
    // (OpenAiChatOptions) is preserved through the merge.
    override fun getOptions(): ChatOptions = delegate.options

    /**
     * Stores a cache entry, treating a lost insert race as success. Stages that fan out (article
     * scoring runs several calls concurrently) can issue byte-identical prompts — duplicate or
     * syndicated articles — so two callers both miss the cache and both insert the same
     * `(prompt_hash, model)` key. The loser hits the UNIQUE constraint, but the row it wanted is
     * already there, so the write is simply redundant and must not fail the LLM call.
     */
    private fun store(entry: LlmCache) {
        try {
            llmCacheRepository.save(entry)
            log.debug("LLM cache miss — stored for model={} hash={}", entry.model, entry.promptHash.take(12))
        } catch (e: RuntimeException) {
            if (!isConstraintViolation(e)) throw e
            log.debug(
                "LLM cache entry for model={} hash={} was already written by a concurrent call",
                entry.model, entry.promptHash.take(12)
            )
        }
    }

    /**
     * Records one request. Timed around the delegate alone, so the cache lookup and the cache write
     * stay out of the figure, and a stage's tool loop yields one record per round-trip rather than
     * one spanning all of them.
     *
     * A cache hit never reached a provider, so it is recorded with a zero duration and marked; the
     * percentile read excludes those rows rather than treating them as very fast requests.
     */
    private fun recordCall(startedAt: Instant, elapsed: Duration, response: ChatResponse, cacheHit: Boolean) {
        val usage = TokenUsage.fromChatResponse(response)
        llmCallLogService.record(
            LlmCallRecord(
                startedAt = startedAt,
                stage = resolvedModel.stage.value,
                provider = resolvedModel.provider,
                model = resolvedModel.model,
                duration = elapsed,
                inputTokens = usage.inputTokens,
                outputTokens = usage.outputTokens,
                reportedCostUsd = usage.reportedCostUsd,
                cacheHit = cacheHit,
                attribution = attribution
            )
        )
    }

    /**
     * Records a request that failed, marked with the kind of failure so the latency read can tell a
     * request that ran out of time from one the provider refused quickly. The two are read back
     * differently: see the percentile query in `LlmCallRepositoryCustom`.
     */
    private fun recordFailedCall(startedAt: Instant, elapsed: Duration, error: Exception) {
        llmCallLogService.record(
            LlmCallRecord(
                startedAt = startedAt,
                stage = resolvedModel.stage.value,
                provider = resolvedModel.provider,
                model = resolvedModel.model,
                duration = elapsed,
                inputTokens = 0,
                outputTokens = 0,
                cacheHit = false,
                outcome = LlmCallOutcome.ERROR,
                errorType = errorTypeOf(error),
                attribution = attribution
            )
        )
    }

    private fun userPromptText(prompt: Prompt): String =
        prompt.instructions
            .filter { it.messageType == MessageType.USER || it.messageType == MessageType.SYSTEM }
            .joinToString("\n") { "${it.messageType}:${it.text ?: ""}" }

    private fun isInitialCall(prompt: Prompt): Boolean =
        prompt.instructions.none {
            it.messageType == MessageType.ASSISTANT || it.messageType == MessageType.TOOL
        }

    private fun hasPendingToolCalls(response: ChatResponse): Boolean {
        val output = response.result?.output ?: return false
        return output is AssistantMessage && output.hasToolCalls()
    }

    /**
     * A cache hit replays the original call's token counts, so it is already costed as though the
     * call ran; replaying the reported cost keeps that semantics (and the budget gate's behaviour)
     * unchanged. The reconstructed [DefaultUsage] has no native usage object to hang the cost from,
     * so it travels on the response metadata instead; [TokenUsage] reads that key first and marks
     * the cost as replayed.
     */
    private fun reconstructResponse(cached: LlmCache): ChatResponse {
        val builder = ChatResponseMetadata.builder()
            .usage(DefaultUsage(
                cached.inputTokens ?: 0,
                cached.outputTokens ?: 0
            ))
        cached.reportedCostUsd?.let { builder.keyValue(TokenUsage.REPORTED_COST_METADATA_KEY, it) }
        return ChatResponse(listOf(Generation(AssistantMessage(cached.response))), builder.build())
    }

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
