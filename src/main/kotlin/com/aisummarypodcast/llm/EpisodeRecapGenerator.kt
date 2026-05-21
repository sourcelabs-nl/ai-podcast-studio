package com.aisummarypodcast.llm

import com.aisummarypodcast.store.Podcast
import org.slf4j.LoggerFactory
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.stereotype.Component
import kotlin.time.measureTimedValue

data class RecapResult(
    val recap: String,
    val usage: TokenUsage,
    val costCents: Int?
)

@Component
class EpisodeRecapGenerator(
    private val chatClientFactory: ChatClientFactory
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun generate(scriptText: String, podcast: Podcast, filterModelDef: ResolvedModel, topicLabels: List<String> = emptyList()): RecapResult {
        log.info("[LLM] Generating recap of previous episode for podcast '{}' ({})", podcast.name, podcast.id)
        val chatClient = chatClientFactory.createForModel(podcast.userId, filterModelDef)
        val prompt = buildPrompt(scriptText, topicLabels)

        val (result, elapsed) = measureTimedValue {
            val chatResponse = chatClient.prompt()
                .user(prompt)
                .options(OpenAiChatOptions.builder().model(filterModelDef.model).build())
                .call()
                .chatResponse()

            val recap = chatResponse?.result?.output?.text
                ?: throw IllegalStateException("Empty response from LLM for episode recap generation")

            val usage = TokenUsage.fromChatResponse(chatResponse)
            val costCents = CostEstimator.estimateLlmCostCents(usage.inputTokens, usage.outputTokens, filterModelDef.cost)
            RecapResult(recap.trim(), usage, costCents)
        }

        log.info("[LLM] Episode recap generated for podcast '{}' ({}) in {}", podcast.name, podcast.id, elapsed)
        return result
    }

    internal fun buildPrompt(scriptText: String, topicLabels: List<String> = emptyList()): String {
        val topicContext = if (topicLabels.isNotEmpty()) {
            val labels = topicLabels.joinToString(", ")
            "\n\nTopics discussed in this episode: $labels\nNaturally reference these topics in your summary where relevant."
        } else ""

        return """
            Summarize the following podcast episode script in 2-3 sentences. Focus on the main topics and key points discussed. Write direct, concise statements without any preamble, meta-commentary, or introductory phrases like "In this episode" or "The podcast covered".$topicContext

            Episode script:
            $scriptText
        """.trimIndent()
    }
}
