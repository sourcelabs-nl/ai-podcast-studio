package com.aisummarypodcast.llm

import com.aisummarypodcast.store.Article
import io.github.resilience4j.kotlin.retry.executeSuspendFunction
import io.github.resilience4j.retry.RetryRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.ai.converter.BeanOutputConverter
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import kotlin.time.measureTimedValue

// Generous ceiling for a legitimate dedup response (dozens of small clusters). Well above any
// real output, but low enough that a repetition loop is cut off in seconds rather than minutes.
private const val DEDUP_MAX_OUTPUT_TOKENS = 8000

// Historical titles are only used for topic recall, but some sources (Twitter/Nitter) put an
// entire post in the title field (observed up to ~5000 chars). Truncate so one outlier can't
// dominate the prompt; the leading words are enough to recognise the topic.
private const val HISTORICAL_TITLE_MAX_CHARS = 150

data class DedupCandidate(
    val id: Long,
    val title: String,
    val summary: String?
)

data class DedupCluster(
    val topic: String = "",
    val status: String = "NEW",
    val previousContext: String? = null,
    val selectedArticleIds: List<Int?> = emptyList()
)

data class DedupResult(
    val clusters: List<DedupCluster> = emptyList()
)

data class FilteredArticle(
    val article: Article,
    val followUpContext: String? = null,
    val topic: String? = null
)

data class DedupFilterResult(
    val filteredArticles: List<FilteredArticle>,
    val usage: TokenUsage
)

/** Articles resolved from the dedup clusters, plus how many repeat selections were discarded. */
data class DedupSelection(
    val articles: List<FilteredArticle>,
    val duplicateSelections: Int
)

@Component
class TopicDedupFilter(
    private val chatClientFactory: ChatClientFactory,
    private val jsonMapper: JsonMapper,
    private val retryRegistry: RetryRegistry
) {

    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun filter(
        candidates: List<Article>,
        historicalArticles: List<Article>,
        userId: String,
        modelDef: ResolvedModel
    ): DedupFilterResult {
        if (candidates.isEmpty()) {
            return DedupFilterResult(emptyList(), TokenUsage(0, 0))
        }

        log.info("[Dedup] Filtering {} candidates against {} historical articles", candidates.size, historicalArticles.size)
        val chatClient = chatClientFactory.createForModel(userId, modelDef)
        val prompt = buildPrompt(candidates, historicalArticles)

        val retry = retryRegistry.retry("topic-dedup")
        val (result, elapsed) = measureTimedValue {
            retry.executeSuspendFunction {
                val converter = BeanOutputConverter(DedupResult::class.java, jsonMapper)
                val chatResponse = withContext(Dispatchers.IO) {
                    chatClient.prompt()
                        .user(prompt)
                        // maxTokens caps a degenerating response (e.g. a repetition loop emitting
                        // hundreds of near-duplicate clusters) so it fails in seconds instead of
                        // streaming for minutes before truncating mid-JSON.
                        .options(
                            OpenAiChatOptions.builder()
                                .model(modelDef.model)
                                .temperature(0.3)
                                .maxTokens(DEDUP_MAX_OUTPUT_TOKENS)
                                // deepseek-v4-flash reasons by default on OpenRouter; its hidden reasoning
                                // tokens count against maxTokens and can consume the whole budget, leaving
                                // no room for the actual JSON output. Disable it explicitly.
                                .reasoningEffort("none")
                        )
                        .call()
                        .responseEntity(converter)
                }

                val dedupResult = chatResponse.entity()
                    ?: throw IllegalStateException("Empty response from LLM for topic dedup filter")

                Pair(dedupResult, TokenUsage.fromChatResponse(chatResponse.response()))
            }
        }

        val (dedupResult, usage) = result
        val selection = selectArticles(dedupResult.clusters, candidates)

        if (selection.duplicateSelections > 0) {
            log.warn("[Dedup] Response selected {} article(s) in more than one cluster — discarded the repeats",
                selection.duplicateSelections)
        }

        log.info("[Dedup] Filter complete in {} — {} candidates → {} selected across {} clusters",
            elapsed, candidates.size, selection.articles.size, dedupResult.clusters.size)

        return DedupFilterResult(selection.articles, usage)
    }

    /**
     * Resolves the clusters' selected article ids back to [candidates], annotating each article with
     * its cluster's topic and (for continuations) its previous context.
     *
     * An article is kept only the first time it is selected. A degenerating dedup response can list
     * the same article across many clusters, which would otherwise return more articles than were
     * fed in and let the downstream compose cap fill every slot with repeats of the same few.
     * Clusters arrive in the model's own relevance order, so the first mention carries the
     * annotation worth keeping.
     */
    internal fun selectArticles(clusters: List<DedupCluster>, candidates: List<Article>): DedupSelection {
        val candidateById = candidates.associateBy { it.id!!.toInt() }
        val articles = mutableListOf<FilteredArticle>()
        val seenArticleIds = mutableSetOf<Int>()
        var duplicateSelections = 0

        for (cluster in clusters) {
            if (cluster.selectedArticleIds.isEmpty()) continue

            val followUpContext = if (cluster.status == "CONTINUATION" && cluster.previousContext != null) {
                cluster.previousContext
            } else null

            for (articleId in cluster.selectedArticleIds.filterNotNull()) {
                val article = candidateById[articleId] ?: continue
                if (!seenArticleIds.add(articleId)) {
                    duplicateSelections++
                    continue
                }
                articles.add(FilteredArticle(article, followUpContext, cluster.topic))
            }
        }

        return DedupSelection(articles, duplicateSelections)
    }

    internal fun buildPrompt(candidates: List<Article>, historicalArticles: List<Article>): String {
        val candidateBlock = candidates.mapIndexed { _, article ->
            "${article.id}. [${extractDomain(article.url)}] ${article.title}\n${article.summary ?: article.body}"
        }.joinToString("\n\n")

        val historicalBlock = if (historicalArticles.isNotEmpty()) {
            // Titles only: the historical block exists for topic recall (has this been covered?),
            // and titles convey the topic. Embedding full summaries here bloated the prompt without
            // improving continuation detection. Titles are truncated so a single oversized
            // source title cannot dominate the prompt.
            val grouped = historicalArticles.joinToString("\n") { article ->
                "- [${extractDomain(article.url)}] ${article.title.truncateForHistory()}"
            }
            """

            Historical articles from recent episodes:
            $grouped
            """
        } else ""

        return """
            You are a topic deduplication filter for a podcast pipeline. Your job is to cluster today's candidate articles by topic, compare against historical articles from recent episodes, and decide what's new vs. already covered.

            For each cluster of related articles, output:
            - "topic": short label for the topic
            - "status": "NEW" (not covered in recent episodes) or "CONTINUATION" (covered before)
            - "previousContext": (CONTINUATION only) one sentence describing what was covered before
            - "selectedArticleIds": article IDs to keep for composition (max 3 per cluster)

            Rules:
            - CONTINUATION topics with NO genuinely new information: set selectedArticleIds to empty []
            - CONTINUATION topics WITH new developments: select up to 3 articles with the new information
            - NEW topics with 3 or fewer articles: keep all
            - NEW topics with more than 3 articles: select the 3 most comprehensive/complementary articles (prefer different sources, different angles)
            - Merge cross-source duplicates into one cluster (e.g., TechCrunch and The Verge covering the same announcement)
            - High-scoring single-source articles are likely unique — don't cluster them with loosely related topics
            - Every candidate article must appear in exactly one cluster

            Respond with a JSON object: { "clusters": [ ... ] }

            Today's candidate articles:
            $candidateBlock
            $historicalBlock
        """.trimIndent()
    }

    private fun String.truncateForHistory(): String =
        if (length <= HISTORICAL_TITLE_MAX_CHARS) this else take(HISTORICAL_TITLE_MAX_CHARS).trimEnd() + "…"

}
