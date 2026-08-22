package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.ScoringProperties
import com.aisummarypodcast.store.Article
import com.aisummarypodcast.store.ArticleRepository
import com.aisummarypodcast.store.Podcast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import org.springframework.ai.converter.BeanOutputConverter
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

data class ScoreSummarizeResult(
    val relevanceScore: Int = 0,
    val summary: String = "",
    val subtopic: String? = null
)

@Component
class ArticleScoreSummarizer(
    private val articleRepository: ArticleRepository,
    private val chatClientFactory: ChatClientFactory,
    private val jsonMapper: JsonMapper,
    appProperties: AppProperties
) {
    private val scoringProperties: ScoringProperties = appProperties.llm.scoring

    companion object {
        private const val LONG_ARTICLE_WORD_THRESHOLD = 1500
        private const val MEDIUM_ARTICLE_WORD_THRESHOLD = 500
    }

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Scores and summarizes the given articles concurrently.
     *
     * @param onProgress invoked as articles finish (success or give-up) with the running
     *   completed count and the total. Throttled to at most ~50 callbacks per run so callers can
     *   stream live progress without flooding the event bus on large batches.
     */
    suspend fun scoreSummarize(
        articles: List<Article>,
        podcast: Podcast,
        filterModelDef: ResolvedModel,
        sourceLabels: Map<String, String> = emptyMap(),
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): List<Article> {
        val chatClient = chatClientFactory.createForModel(podcast.userId, filterModelDef)
        val model = filterModelDef.model
        val semaphore = Semaphore(scoringProperties.concurrency)
        val maxRetries = scoringProperties.maxRetries

        val total = articles.size
        val completed = AtomicInteger(0)
        val progressStep = maxOf(1, total / 50)

        return withContext(Dispatchers.IO) {
            supervisorScope {
                articles.map { article ->
                    async {
                        semaphore.withPermit {
                            val sourceLabel = sourceLabels[article.sourceId]
                            log.info("[LLM] Scoring and summarizing article {}: '{}' (source: {})", article.id, article.title, sourceLabel ?: article.sourceId)
                            try {
                                val prompt = buildPrompt(article, podcast)

                                var lastException: Exception? = null
                                for (attempt in 1..maxRetries) {
                                    try {
                                        val converter = BeanOutputConverter(ScoreSummarizeResult::class.java, jsonMapper)
                                        val responseEntity = chatClient.prompt()
                                            .user(promptForAttempt(prompt, attempt))
                                            .options(
                                                OpenAiChatOptions.builder()
                                                    .model(model)
                                                    .temperature(0.3)
                                            )
                                            .call()
                                            .responseEntity(converter)

                                        val result = responseEntity.entity()
                                        val usage = TokenUsage.fromChatResponse(responseEntity.response())
                                        // Prefer the provider's own charge over the configured rates; the reported
                                        // value is also persisted so the score stage can be aggregated correctly.
                                        val costCents = CostEstimator.resolveLlmCost(usage, filterModelDef.cost).costCents?.roundToInt()

                                        val score = result?.relevanceScore ?: 0
                                        val summary = result?.summary?.takeIf { it.isNotBlank() }
                                        val subtopic = normalizeSubtopic(result?.subtopic, podcast)

                                        val updated = article.copy(
                                            relevanceScore = score,
                                            summary = summary,
                                            subtopic = subtopic,
                                            llmInputTokens = (article.llmInputTokens ?: 0) + usage.inputTokens,
                                            llmOutputTokens = (article.llmOutputTokens ?: 0) + usage.outputTokens,
                                            llmCostCents = CostEstimator.addNullableCosts(article.llmCostCents, costCents),
                                            llmReportedCostUsd = CostEstimator.addNullableReportedCosts(
                                                article.llmReportedCostUsd, usage.reportedCostUsd
                                            )
                                        )
                                        articleRepository.save(updated)

                                        log.info("[LLM] Article '{}' scored {} — summary: {} chars (source: {})", article.title, score, summary?.length ?: 0, sourceLabel ?: article.sourceId)
                                        return@withPermit updated
                                    } catch (e: Exception) {
                                        lastException = e
                                        if (attempt < maxRetries) {
                                            val backoffMs = 1000L * (1 shl (attempt - 1))
                                            log.warn("[LLM] Retry {}/{} for article '{}' (source: {}): {}", attempt, maxRetries, article.title, sourceLabel ?: article.sourceId, e.message)
                                            delay(backoffMs)
                                        }
                                    }
                                }
                                throw lastException!!
                            } catch (e: Exception) {
                                log.error("[LLM] Error scoring/summarizing article '{}' (source: {}): {}", article.title, sourceLabels[article.sourceId] ?: article.sourceId, e.message, e)
                                null
                            } finally {
                                val done = completed.incrementAndGet()
                                if (done % progressStep == 0 || done == total) {
                                    onProgress(done, total)
                                }
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
            }
        }
    }

    /**
     * Returns the prompt to send on [attempt], appending a correction from the second attempt on.
     *
     * A retry must never send the byte-identical prompt. [CachingChatModel] keys on prompt text, so
     * a model that answered with prose instead of JSON has that unparseable answer cached: every
     * retry would replay it from cache and fail identically in milliseconds, and because a failed
     * article keeps a null `relevanceScore` it is picked up again by every later pipeline run,
     * leaving it permanently unscorable. Naming the attempt keeps each retry's prompt distinct, so
     * every attempt is a real call, and telling the model what went wrong makes the retry likelier
     * to succeed.
     */
    internal fun promptForAttempt(prompt: String, attempt: Int): String =
        if (attempt <= 1) prompt else "$prompt\n\n${jsonOnlyCorrection(attempt)}"

    private fun jsonOnlyCorrection(attempt: Int): String =
        "Retry $attempt: your previous response could not be parsed as JSON. Respond with the raw " +
            "JSON object only. Do not include reasoning, commentary, or markdown code fences, and " +
            "do not write anything before or after the JSON."

    internal fun buildPrompt(article: Article, podcast: Podcast): String {
        val isAggregated = article.title.startsWith("Posts from")
        val authorContext = article.author?.let { "by $it" } ?: ""

        val contentBlock = if (isAggregated) {
            val postContext = if (authorContext.isNotEmpty()) {
                "The following content consists of multiple social media posts $authorContext."
            } else {
                "The following content consists of multiple social media posts."
            }
            "$postContext\n\n${article.body}"
        } else {
            val titleLine = "Content title: ${article.title}"
            val authorLine = if (authorContext.isNotEmpty()) "\nContent author: ${article.author}" else ""
            "$titleLine$authorLine\nContent: ${article.body}"
        }

        val wordCount = article.body.split("\\s+".toRegex()).size
        val summaryLengthInstruction = when {
            wordCount >= LONG_ARTICLE_WORD_THRESHOLD -> "a full paragraph covering key points, context, and attribution"
            wordCount >= MEDIUM_ARTICLE_WORD_THRESHOLD -> "4-6 sentences"
            else -> "2-3 sentences"
        }

        val subtopicNames = podcast.subtopics?.weights?.keys?.toList().orEmpty()
        val subtopicsConfigured = subtopicNames.isNotEmpty()

        val subtopicBlock = if (subtopicsConfigured) {
            val list = subtopicNames.joinToString("\n") { "  - $it" }
            "\n\nThe podcast covers the following subtopics within this topic:\n$list\n\nClassify the content into the best-matching subtopic name (verbatim from the list above), or null if none of them reasonably apply."
        } else {
            ""
        }

        val schemaLines = buildString {
            append("- \"relevanceScore\" (integer 0-10)\n")
            if (subtopicsConfigured) {
                append("- \"subtopic\" (one of the subtopic names listed above, or null if none apply)\n")
            }
            append("- \"summary\" ($summaryLengthInstruction of direct, factual statements about the key relevant information)")
        }

        return """
            You are a relevance scorer and summarizer. Given the topic of interest and content, perform the following:
            1. Rate the content's relevance to the topic on a scale of 0-10
            2. Summarize the relevant information in $summaryLengthInstruction, filtering out any irrelevant parts

            Write directly about what happened — say "Anthropic launched X" not "The article discusses Anthropic launching X".

            Topic of interest: ${podcast.topic}$subtopicBlock

            $contentBlock

            Respond with a JSON object containing:
            $schemaLines

            If the content attributes information to a specific person, organization, or study, preserve that attribution in your summary.
            If the content is completely irrelevant (score 0-2), you may leave the summary empty.
        """.trimIndent()
    }

    internal fun normalizeSubtopic(raw: String?, podcast: Podcast): String? {
        val configured = podcast.subtopics?.weights?.keys ?: return null
        if (configured.isEmpty()) return null
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (value.equals("null", ignoreCase = true)) return null
        return configured.firstOrNull { it.equals(value, ignoreCase = true) }
    }
}
