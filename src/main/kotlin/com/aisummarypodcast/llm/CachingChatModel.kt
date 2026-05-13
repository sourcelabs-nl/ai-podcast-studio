package com.aisummarypodcast.llm

import com.aisummarypodcast.store.LlmCache
import com.aisummarypodcast.store.LlmCacheRepository
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

/**
 * Wraps the underlying [ChatModel] with a SQLite-backed cache. Safe under Spring AI
 * tool-call loops: the cache key derives only from `USER` and `SYSTEM` messages, so identical
 * compose prompts hash to the same key regardless of how the tool loop unfolds. Cache lookup
 * only fires on the initial call (no `ASSISTANT`/`TOOL` messages yet); the cached value is
 * the first response in the loop that contains no pending tool calls (i.e. the final
 * assistant turn).
 */
class CachingChatModel(
    private val delegate: ChatModel,
    private val llmCacheRepository: LlmCacheRepository
) : ChatModel {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun call(prompt: Prompt): ChatResponse {
        val model = prompt.options?.model ?: "default"
        val promptHash = sha256("$model:${userPromptText(prompt)}")
        val initialCall = isInitialCall(prompt)

        if (initialCall) {
            val cached = llmCacheRepository.findByPromptHashAndModel(promptHash, model)
            if (cached != null) {
                log.debug("LLM cache hit for model={} hash={}", model, promptHash.take(12))
                return reconstructResponse(cached)
            }
        }

        val response = delegate.call(prompt)

        if (!hasPendingToolCalls(response)) {
            val responseText = response.result?.output?.text
            if (responseText != null) {
                val usage = response.metadata?.usage
                llmCacheRepository.save(
                    LlmCache(
                        promptHash = promptHash,
                        model = model,
                        response = responseText,
                        createdAt = Instant.now().toString(),
                        inputTokens = usage?.promptTokens?.toInt(),
                        outputTokens = usage?.completionTokens?.toInt()
                    )
                )
                log.debug("LLM cache miss — stored for model={} hash={}", model, promptHash.take(12))
            }
        }

        return response
    }

    override fun stream(prompt: Prompt): Flux<ChatResponse> = delegate.stream(prompt)

    override fun getDefaultOptions(): ChatOptions = delegate.defaultOptions

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

    private fun reconstructResponse(cached: LlmCache): ChatResponse {
        val metadata = ChatResponseMetadata.builder()
            .usage(DefaultUsage(
                cached.inputTokens ?: 0,
                cached.outputTokens ?: 0
            ))
            .build()
        return ChatResponse(listOf(Generation(AssistantMessage(cached.response))), metadata)
    }

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
