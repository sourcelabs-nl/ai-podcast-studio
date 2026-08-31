package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.store.Article
import io.github.resilience4j.kotlin.retry.executeSuspendFunction
import io.github.resilience4j.retry.RetryRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.ai.converter.BeanOutputConverter
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import tools.jackson.core.JsonToken
import tools.jackson.databind.json.JsonMapper
import kotlin.time.measureTimedValue

// The prompt requires every candidate article to appear in exactly one cluster, so a legitimate
// response's length is proportional to its input, and on days where little clusters together it
// approaches one cluster per candidate (observed: 37 candidates → 29 clusters, 39 → 39, 56 → 50,
// 68 → 44). At roughly 90 output tokens per cluster — topic label, status, a CONTINUATION's
// previousContext sentence, the selected ids — a 183-candidate day needs some 16,000 tokens, so
// the budget has to scale with the candidate count. A fixed 8000-token cap truncated episode 191's
// response mid-array at cluster 234 and cost the whole episode.
private const val DEDUP_TOKENS_PER_CANDIDATE = 90
private const val DEDUP_MIN_OUTPUT_TOKENS = 8000

// Hard ceiling, so a degenerating response (a repetition loop emitting near-duplicate clusters
// indefinitely) is still cut off in seconds rather than streaming for minutes. Far inside the
// dedup model's 1M-token context window.
private const val DEDUP_MAX_OUTPUT_TOKENS = 32000

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
    private val retryRegistry: RetryRegistry,
    private val appProperties: AppProperties
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
                val chatResponse = withContext(Dispatchers.IO) {
                    chatClient.prompt()
                        .user(prompt)
                        // maxTokens caps a degenerating response (e.g. a repetition loop emitting
                        // hundreds of near-duplicate clusters) so it fails in seconds instead of
                        // streaming for minutes before truncating mid-JSON. The budget scales with
                        // the candidate count so a legitimate large response still fits.
                        .options(
                            OpenAiChatOptions.builder()
                                .model(modelDef.model)
                                .temperature(0.3)
                                .maxTokens(dedupOutputTokenBudget(candidates.size))
                                // deepseek-v4-flash reasons by default on OpenRouter; its hidden reasoning
                                // tokens count against maxTokens and can consume the whole budget, leaving
                                // no room for the actual JSON output. Disable it explicitly.
                                .reasoningEffort("none")
                        )
                        .call()
                        .chatResponse()
                }

                val raw = chatResponse?.result?.output?.text?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Empty response from LLM for topic dedup filter")

                Pair(parseOrSalvage(raw, candidates), TokenUsage.fromChatResponse(chatResponse))
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
     * Output-token budget for a dedup call over [candidateCount] candidates, clamped to
     * [DEDUP_MIN_OUTPUT_TOKENS]..[DEDUP_MAX_OUTPUT_TOKENS].
     */
    internal fun dedupOutputTokenBudget(candidateCount: Int): Int =
        (candidateCount * DEDUP_TOKENS_PER_CANDIDATE)
            .coerceIn(DEDUP_MIN_OUTPUT_TOKENS, DEDUP_MAX_OUTPUT_TOKENS)

    /**
     * Parses the dedup response, recovering what it can from a response the model truncated.
     *
     * A truncated response is safe to act on in a way most stages' output is not: an article that no
     * surviving cluster mentions is simply not selected, so the loss is material the episode does not
     * cover, never a corrupted script. Discarding the whole payload instead — which is what parsing
     * strictly and letting the error fly did — cost episode 191 its 234 complete clusters.
     *
     * A salvage is only accepted once it still selects at least `app.compose.max-articles` articles.
     * At that point the lost tail provably could not have changed the episode, because [LlmPipeline]
     * caps the compose input to that same number and would have dropped the surplus anyway. Below it
     * the truncation really did cost material, so the caller's retry gets a turn instead.
     */
    private fun parseOrSalvage(raw: String, candidates: List<Article>): DedupResult {
        val strict = try {
            BeanOutputConverter(DedupResult::class.java, jsonMapper).convert(raw)
        } catch (e: JacksonException) {
            log.warn("[Dedup] Response did not parse ({}) — attempting to salvage the complete clusters", e.message)
            null
        }
        if (strict != null) return strict

        val salvaged = salvageClusters(raw)
        val selected = selectArticles(salvaged, candidates).articles.size
        val required = appProperties.compose.maxArticles
        if (selected < required) {
            throw IllegalStateException(
                "Truncated dedup response salvaged only $selected selectable article(s) from " +
                    "${salvaged.size} cluster(s), below the $required needed to compose"
            )
        }

        log.warn("[Dedup] Salvaged {} complete cluster(s) selecting {} article(s) from a truncated response — " +
            "the lost tail is beyond the compose cap of {} and cannot change the episode",
            salvaged.size, selected, required)
        return DedupResult(salvaged)
    }

    /**
     * Recovers the complete [DedupCluster] elements from a `clusters` array the model cut off
     * mid-element, by reading the array one element at a time and stopping where the JSON runs out.
     */
    internal fun salvageClusters(raw: String): List<DedupCluster> {
        val start = raw.indexOf('{')
        if (start < 0) return emptyList()

        val clusters = mutableListOf<DedupCluster>()
        try {
            jsonMapper.createParser(raw.substring(start)).use { parser ->
                var inClusters = false
                while (parser.nextToken() != null) {
                    if (!inClusters) {
                        val atClusters = parser.currentToken() == JsonToken.PROPERTY_NAME &&
                            parser.currentName() == "clusters"
                        if (atClusters && parser.nextToken() == JsonToken.START_ARRAY) inClusters = true
                        continue
                    }
                    when (parser.currentToken()) {
                        // readValueAs, not jsonMapper.readValue: the latter treats the parser as a
                        // whole document and rejects the array's remaining tokens as trailing input.
                        JsonToken.START_OBJECT -> clusters.add(parser.readValueAs(DedupCluster::class.java))
                        JsonToken.END_ARRAY -> return clusters
                        else -> {}
                    }
                }
            }
        } catch (_: JacksonException) {
            // Expected: the array is cut off part-way through an element. Everything read before it
            // is complete and usable.
        }
        return clusters
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
