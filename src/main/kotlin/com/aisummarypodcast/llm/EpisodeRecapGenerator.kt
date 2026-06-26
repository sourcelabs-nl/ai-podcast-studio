package com.aisummarypodcast.llm

import com.aisummarypodcast.store.Podcast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.stereotype.Component
import kotlin.time.measureTimedValue

data class RecapResult(
    val recap: String,
    val usage: TokenUsage,
    val costCents: Int?,
    val coveredTopics: List<String> = emptyList()
)

@Component
class EpisodeRecapGenerator(
    private val chatClientFactory: ChatClientFactory
) {

    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun generate(scriptText: String, podcast: Podcast, filterModelDef: ResolvedModel, topicLabels: List<String> = emptyList()): RecapResult {
        log.info("[LLM] Generating recap of previous episode for podcast '{}' ({})", podcast.name, podcast.id)
        val chatClient = chatClientFactory.createForModel(podcast.userId, filterModelDef)
        val prompt = buildPrompt(scriptText, topicLabels)

        val (result, elapsed) = measureTimedValue {
            val chatResponse = withContext(Dispatchers.IO) {
                chatClient.prompt()
                    .user(prompt)
                    .options(OpenAiChatOptions.builder().model(filterModelDef.model))
                    .call()
                    .chatResponse()
            }

            val rawResponse = chatResponse?.result?.output?.text
                ?: throw IllegalStateException("Empty response from LLM for episode recap generation")

            val extraction = CoveredTopicsExtractor.extract(rawResponse)
            val usage = TokenUsage.fromChatResponse(chatResponse)
            val costCents = CostEstimator.estimateLlmCostCents(usage.inputTokens, usage.outputTokens, filterModelDef.cost)
            RecapResult(extraction.recap, usage, costCents, extraction.coveredTopics)
        }

        log.info("[LLM] Episode recap generated for podcast '{}' ({}) in {}", podcast.name, podcast.id, elapsed)
        return result
    }

    internal fun buildPrompt(scriptText: String, topicLabels: List<String> = emptyList()): String {
        val topicContext = if (topicLabels.isNotEmpty()) {
            val labelList = topicLabels.joinToString("\n") { "- $it" }
            """


            Candidate topics (some may NOT actually be discussed in the script):
            $labelList

            Naturally reference the discussed topics in your summary where relevant. Then, after the summary, append a metadata block listing ONLY the candidate topics above that are genuinely discussed in the script (a topic counts as discussed only if the script talks about it, not merely if it sounds related). Use the EXACT labels above, do not rename or rephrase them. Omit any candidate the script does not discuss.

            Format:
            |||COVERED_TOPICS|||
            ["first discussed topic", "second discussed topic", ...]
            |||END_COVERED_TOPICS|||"""
        } else ""

        return """
            Summarize the following podcast episode script in 2-3 sentences. Focus on the main topics and key points discussed. Write direct, concise statements without any preamble, meta-commentary, or introductory phrases like "In this episode" or "The podcast covered".$topicContext

            Episode script:
            $scriptText
        """.trimIndent()
    }
}
