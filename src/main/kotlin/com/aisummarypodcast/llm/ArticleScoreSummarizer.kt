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
import io.github.resilience4j.kotlin.retry.executeSuspendFunction
import io.github.resilience4j.retry.RetryRegistry
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
    val subtopic: String? = null,
    /** A [NewsType] name; see the enum for why the scoring stage is the only place this can be decided. */
    val newsType: String? = null
)

@Component
class ArticleScoreSummarizer(
    private val articleRepository: ArticleRepository,
    private val chatClientFactory: ChatClientFactory,
    private val jsonMapper: JsonMapper,
    private val retryRegistry: RetryRegistry,
    appProperties: AppProperties
) {
    private val scoringProperties: ScoringProperties = appProperties.llm.scoring

    companion object {
        private const val LONG_ARTICLE_WORD_THRESHOLD = 1500
        private const val MEDIUM_ARTICLE_WORD_THRESHOLD = 500
    }

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Scores and summarizes the given articles concurrently against the podcast's topic, and
     * persists the result onto each article.
     *
     * @param onProgress invoked as articles finish (success or give-up) with the running
     *   completed count and the total. Throttled to at most ~50 callbacks per run so callers can
     *   stream live progress without flooding the event bus on large batches.
     */
    suspend fun scoreSummarize(
        articles: List<Article>,
        podcast: Podcast,
        filterModelDef: ResolvedModel,
        context: ScoringContext = ScoringContext(),
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): List<Article> =
        scoreConcurrently(articles, podcast, filterModelDef, context, podcast.topic, onProgress) { article, scored ->
            val costCents = CostEstimator.resolveLlmCost(scored.usage, filterModelDef.cost).costCents?.roundToInt()
            val updated = scored.article.copy(
                llmInputTokens = (article.llmInputTokens ?: 0) + scored.usage.inputTokens,
                llmOutputTokens = (article.llmOutputTokens ?: 0) + scored.usage.outputTokens,
                llmCostCents = CostEstimator.addNullableCosts(article.llmCostCents, costCents),
                llmReportedCostUsd = CostEstimator.addNullableReportedCosts(
                    article.llmReportedCostUsd, scored.usage.reportedCostUsd
                )
            )
            articleRepository.save(updated)
        }.map { it.article }

    /**
     * Scores and summarizes [articles] against [focus] instead of the podcast's topic, for a focus
     * episode. Nothing is persisted: the stored `relevance_score`, `summary` and `subtopic` mean
     * relevance to the podcast's own topic, and every regular episode reads them on those terms.
     * Each returned article is an in-memory copy carrying its focus score and summary, with the
     * usage its call cost.
     */
    suspend fun scoreForFocus(
        articles: List<Article>,
        focus: String,
        podcast: Podcast,
        filterModelDef: ResolvedModel,
        context: ScoringContext = ScoringContext(),
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): List<FocusScoredArticle> =
        scoreConcurrently(articles, podcast, filterModelDef, context, focus, onProgress) { _, scored -> scored.article }

    private suspend fun scoreConcurrently(
        articles: List<Article>,
        podcast: Podcast,
        filterModelDef: ResolvedModel,
        context: ScoringContext,
        topicOfInterest: String,
        onProgress: (completed: Int, total: Int) -> Unit,
        onScored: (original: Article, scored: FocusScoredArticle) -> Article
    ): List<FocusScoredArticle> {
        val semaphore = Semaphore(scoringProperties.concurrency)
        val total = articles.size
        val completed = AtomicInteger(0)
        val progressStep = maxOf(1, total / 50)

        return withContext(Dispatchers.IO) {
            supervisorScope {
                articles.map { article ->
                    async {
                        semaphore.withPermit {
                            val sourceLabel = context.sourceLabels[article.sourceId]
                            log.info("[LLM] Scoring and summarizing article {}: '{}' (source: {})", article.id, article.title, sourceLabel ?: article.sourceId)
                            try {
                                val scored = scoreOne(article, podcast, filterModelDef, context, topicOfInterest)
                                val result = scored.copy(article = onScored(article, scored))
                                log.info("[LLM] Article '{}' scored {} as {} — summary: {} chars (source: {})", article.title, result.article.relevanceScore, result.article.newsType ?: "UNCLASSIFIED", result.article.summary?.length ?: 0, sourceLabel ?: article.sourceId)
                                result
                            } catch (e: Exception) {
                                log.error("[LLM] Error scoring/summarizing article '{}' (source: {}): {}", article.title, sourceLabel ?: article.sourceId, e.message, e)
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

    /** One scoring call for [article] against [topicOfInterest]; the returned copy is not persisted. */
    private suspend fun scoreOne(
        article: Article,
        podcast: Podcast,
        filterModelDef: ResolvedModel,
        context: ScoringContext,
        topicOfInterest: String
    ): FocusScoredArticle {
        // A client per article, so each recorded request names the article it scored. That is what
        // lets an episode's scoring requests be found at all: they are issued when the article
        // arrives, before any episode exists to name. Building one is object construction against an
        // HTTP round trip.
        val chatClient = chatClientFactory.createForModel(
            podcast.userId,
            filterModelDef,
            attribution = LlmCallAttribution(episodeId = context.episodeId, articleId = article.id)
        )
        val prompt = buildPrompt(article, podcast, topicOfInterest)

        // Resilience4j owns the attempt count and backoff. The attempt number is still tracked here
        // because each retry escalates the prompt with a "raw JSON only" correction, which the retry
        // API does not expose.
        var attempt = 0
        return retryRegistry.retry("article-scoring").executeSuspendFunction {
            attempt++
            val converter = BeanOutputConverter(ScoreSummarizeResult::class.java, jsonMapper)
            val responseEntity = chatClient.prompt()
                .user(promptForAttempt(prompt, attempt))
                .options(
                    OpenAiChatOptions.builder()
                        .model(filterModelDef.model)
                        .temperature(0.3)
                        // A JSON score needs no deliberation, and reasoning tokens are charged as
                        // output. Stated rather than left to whichever endpoint OpenRouter picked.
                        .withRoutingAndReasoning(filterModelDef)
                )
                .call()
                .responseEntity(converter)

            val result = responseEntity.entity()
            // Prefer the provider's own charge over the configured rates; the reported value is also
            // persisted so the score stage can be aggregated correctly.
            val usage = TokenUsage.fromChatResponse(responseEntity.response())
            FocusScoredArticle(
                article = article.copy(
                    relevanceScore = result?.relevanceScore ?: 0,
                    summary = result?.summary?.takeIf { it.isNotBlank() },
                    subtopic = normalizeSubtopic(result?.subtopic, podcast),
                    newsType = NewsType.parse(result?.newsType)?.name
                ),
                usage = usage
            )
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

    internal fun buildPrompt(article: Article, podcast: Podcast, topicOfInterest: String = podcast.topic): String {
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
            append("- \"newsType\" (exactly one of \"DEVELOPMENT\", \"RETROSPECTIVE\", \"EVERGREEN\")\n")
            if (subtopicsConfigured) {
                append("- \"subtopic\" (one of the subtopic names listed above, or null if none apply)\n")
            }
            append("- \"summary\" ($summaryLengthInstruction of direct, factual statements about the key relevant information)")
        }

        return """
            You are a relevance scorer and summarizer. Given the topic of interest and content, perform the following:
            1. Rate the content's relevance to the topic on a scale of 0-10
            2. Classify what the content is in time, as "newsType"
            3. Summarize the relevant information in $summaryLengthInstruction, filtering out any irrelevant parts

            Classify "newsType" as exactly one of:
            - "DEVELOPMENT": it reports something that has just happened — a launch, release, announcement, publication, incident or result presented as new.
            - "RETROSPECTIVE": it analyses, benchmarks, reviews or comments on something that was already released or already known. The analysis itself may be new; the thing it examines is not. Judge this from the content's own words: phrases like "when X was released", "after using it for a while", "revisiting", or a report about an already-shipped product are all RETROSPECTIVE.
            - "EVERGREEN": it is a landing page, README, documentation or marketing copy describing something that exists, with no datable event in it at all. Star counts, feature lists, install instructions and compatibility tables are not events.

            Write directly about the subject — say "Anthropic launched X", not "The article discusses Anthropic launching X". But never assert an event the content does not report:
            - For a DEVELOPMENT, state what happened.
            - For a RETROSPECTIVE, lead with what is newly revealed or measured, and do NOT describe the underlying thing as newly released. Write "a technical report on X details..." or "a benchmark of X found...", never "X was released".
            - For an EVERGREEN page, describe what the thing is, and do not imply anything happened.

            Topic of interest: $topicOfInterest$subtopicBlock

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
