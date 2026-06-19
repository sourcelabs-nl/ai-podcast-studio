package com.aisummarypodcast.llm

import com.aisummarypodcast.store.Article
import org.slf4j.LoggerFactory
import org.springframework.ai.converter.BeanOutputConverter
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import kotlin.time.measureTimedValue

// Generous ceiling for a legitimate dedup response (dozens of small clusters). Well above any
// real output, but low enough that a repetition loop is cut off in seconds rather than minutes.
private const val DEDUP_MAX_OUTPUT_TOKENS = 8000

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

@Component
class TopicDedupFilter(
    private val chatClientFactory: ChatClientFactory,
    private val jsonMapper: JsonMapper
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun filter(
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

        val maxRetries = 3
        val (result, elapsed) = measureTimedValue {
            var lastException: Exception? = null
            for (attempt in 1..maxRetries) {
                try {
                    val converter = BeanOutputConverter(DedupResult::class.java, jsonMapper)
                    val chatResponse = chatClient.prompt()
                        .user(prompt)
                        // maxTokens caps a degenerating response (e.g. a repetition loop emitting
                        // hundreds of near-duplicate clusters) so it fails in seconds instead of
                        // streaming for minutes before truncating mid-JSON.
                        .options(OpenAiChatOptions.builder().model(modelDef.model).temperature(0.3).maxTokens(DEDUP_MAX_OUTPUT_TOKENS).build())
                        .call()
                        .responseEntity(converter)

                    val dedupResult = chatResponse.entity()
                        ?: throw IllegalStateException("Empty response from LLM for topic dedup filter")

                    val usage = TokenUsage.fromChatResponse(chatResponse.response())
                    return@measureTimedValue Pair(dedupResult, usage)
                } catch (e: Exception) {
                    lastException = e
                    if (attempt < maxRetries) {
                        val backoffMs = 1000L * (1 shl (attempt - 1))
                        log.warn("[Dedup] Retry {}/{} for topic dedup filter: {}", attempt, maxRetries, e.message)
                        Thread.sleep(backoffMs)
                    }
                }
            }
            throw lastException!!
        }

        val (dedupResult, usage) = result
        val candidateById = candidates.associateBy { it.id!!.toInt() }
        val filteredArticles = mutableListOf<FilteredArticle>()

        for (cluster in dedupResult.clusters) {
            if (cluster.selectedArticleIds.isEmpty()) continue

            val followUpContext = if (cluster.status == "CONTINUATION" && cluster.previousContext != null) {
                cluster.previousContext
            } else null

            for (articleIndex in cluster.selectedArticleIds.filterNotNull()) {
                val article = candidateById[articleIndex]
                if (article != null) {
                    filteredArticles.add(FilteredArticle(article, followUpContext, cluster.topic))
                }
            }
        }

        log.info("[Dedup] Filter complete in {} — {} candidates → {} selected across {} clusters",
            elapsed, candidates.size, filteredArticles.size, dedupResult.clusters.size)

        return DedupFilterResult(filteredArticles, usage)
    }

    internal fun buildPrompt(candidates: List<Article>, historicalArticles: List<Article>): String {
        val candidateBlock = candidates.mapIndexed { _, article ->
            "${article.id}. [${extractDomain(article.url)}] ${article.title}\n${article.summary ?: article.body}"
        }.joinToString("\n\n")

        val historicalBlock = if (historicalArticles.isNotEmpty()) {
            val grouped = historicalArticles.joinToString("\n") { article ->
                "- [${extractDomain(article.url)}] ${article.title}: ${article.summary ?: "(no summary)"}"
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

}
