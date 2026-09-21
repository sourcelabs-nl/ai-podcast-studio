package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.store.Article
import io.github.resilience4j.kotlin.retry.executeSuspendFunction
import io.github.resilience4j.retry.RetryRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import tools.jackson.core.JsonToken
import tools.jackson.core.type.TypeReference
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
// indefinitely) is still cut off rather than streaming until the request timeout. Far inside the
// dedup model's 1M-token context window.
private const val DEDUP_MAX_OUTPUT_TOKENS = 32000

// Historical titles are only used for topic recall, but some sources (Twitter/Nitter) put an
// entire post in the title field (observed up to ~5000 chars). Truncate so one outlier can't
// dominate the prompt; the leading words are enough to recognise the topic.
private const val HISTORICAL_TITLE_MAX_CHARS = 150

// Spring AI carries OpenAI's finish reason through as the enum's name, so a normal completion
// reports "STOP" and a response cut off at maxTokens reports "LENGTH".
private const val NORMAL_FINISH_REASON = "STOP"

// The one cluster status for which an empty selection is a contract violation. The prompt allows a
// CONTINUATION to select nothing ("no genuinely new information"); a NEW cluster must keep all its
// articles, or the three most comprehensive.
private const val NEW_CLUSTER_STATUS = "NEW"

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
    val usage: TokenUsage,
    /**
     * What the already-covered gate charged, kept apart from [usage] rather than folded into it.
     *
     * The gate runs on a different model at different rates, so adding its tokens to [usage] would
     * both misreport the clustering call's size and make [CostEstimator.resolveLlmCost] price them
     * at the dedup model's rate on any call the provider did not report. Null means the gate did
     * not run or reported nothing, which must stay distinct from a zero.
     */
    val gateReportedCostUsd: Double? = null
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
    private val appProperties: AppProperties,
    private val coveredTopicGate: CoveredTopicGate
) {

    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun filter(
        candidates: List<Article>,
        history: EpisodeHistory,
        userId: String,
        modelDef: ResolvedModel,
        episodeId: Long? = null
    ): DedupFilterResult {
        if (candidates.isEmpty()) {
            return DedupFilterResult(emptyList(), TokenUsage(0, 0))
        }

        log.info("[Dedup] Filtering {} candidates against {} historical articles and {} covered topic(s)",
            candidates.size, history.articles.size, history.coveredTopics.size)

        // The already-covered decision is the one closed question in this stage, so it is answered
        // before the clustering call rather than by it. A gate that answers nothing leaves every
        // candidate here, which is exactly how the stage behaved before it existed.
        val gate = withContext(Dispatchers.IO) {
            coveredTopicGate.evaluate(candidates, history.coveredTopics, userId, episodeId)
        }
        val gatedCandidates = keepGateFromEmptyingTheEpisode(candidates, gate)

        val chatClient = chatClientFactory.createForModel(userId, modelDef, episodeId = episodeId)
        val prompt = buildPrompt(gatedCandidates, history)

        val outputTokenBudget = dedupOutputTokenBudget(gatedCandidates.size)
        val retry = retryRegistry.retry("topic-dedup")
        val (result, elapsed) = measureTimedValue {
            // Resilience4j owns the attempt count and backoff. The attempt number is still tracked
            // here because each retry escalates the prompt with a "raw JSON only" correction, which
            // the retry API does not expose.
            var attempt = 0
            retry.executeSuspendFunction {
                attempt++
                val chatResponse = withContext(Dispatchers.IO) {
                    chatClient.prompt()
                        .user(promptForAttempt(prompt, attempt))
                        // maxTokens caps a degenerating response (e.g. a repetition loop emitting
                        // hundreds of near-duplicate clusters) so it is cut off instead of streaming
                        // until the request timeout. The budget scales with the candidate count so a
                        // legitimate large response still fits.
                        .options(
                            OpenAiChatOptions.builder()
                                .model(modelDef.model)
                                .temperature(0.3)
                                .maxTokens(outputTokenBudget)
                                // Stated, not left to the model: this model reasons at high effort
                                // by default, and those tokens are charged against maxTokens, which
                                // consumed the whole budget and returned empty content. Measured at
                                // 0 reasoning tokens with an explicit effort of "none".
                                .withRoutingAndReasoning(modelDef.provider, OpenRouterRouting.NO_REASONING)
                        )
                        .call()
                        .chatResponse()
                }

                logAbnormalFinishReason(chatResponse, outputTokenBudget)

                val raw = chatResponse?.result?.output?.text?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Empty response from LLM for topic dedup filter")

                Pair(parseOrSalvage(raw, gatedCandidates), TokenUsage.fromChatResponse(chatResponse))
            }
        }

        val (dedupResult, usage) = result
        val selection = selectArticles(dedupResult.clusters, gatedCandidates)

        if (selection.duplicateSelections > 0) {
            log.warn("[Dedup] Response selected {} article(s) in more than one cluster — discarded the repeats",
                selection.duplicateSelections)
        }

        log.info("[Dedup] Filter complete in {} — {} candidates ({}) → {} selected across {} clusters",
            elapsed, candidates.size, describeGate(candidates, gatedCandidates, gate),
            selection.articles.size, dedupResult.clusters.size)

        return DedupFilterResult(selection.articles, usage, gate.reportedCostUsd)
    }

    /**
     * Warns when the response did not stop normally, naming [outputTokenBudget].
     *
     * A response the model cut off at the cap and a transport fault are otherwise indistinguishable:
     * both surface as `OpenAIInvalidDataException("Error reading response")`, which is why episode
     * 200's five failed attempts gave no sign that the budget was the cause. The finish reason is
     * the field that separates them, and it is only worth logging when it is not a normal stop.
     */
    private fun logAbnormalFinishReason(chatResponse: ChatResponse?, outputTokenBudget: Int) {
        val finishReason = abnormalFinishReason(chatResponse) ?: return

        log.warn("[Dedup] Response finished with '{}' against an output budget of {} tokens",
            finishReason, outputTokenBudget)
    }

    /**
     * [chatResponse]'s finish reason when it is worth warning about, else null.
     *
     * A normal stop is not worth warning about, and neither is a missing one: a provider that
     * reports nothing tells us nothing about the budget, and warning on every such call would bury
     * the `LENGTH` case this exists to surface.
     */
    internal fun abnormalFinishReason(chatResponse: ChatResponse?): String? =
        chatResponse?.result?.metadata?.finishReason
            ?.takeIf { it.isNotBlank() && !it.equals(NORMAL_FINISH_REASON, ignoreCase = true) }

    /**
     * Output-token budget for a dedup call over [candidateCount] candidates, clamped to
     * [DEDUP_MIN_OUTPUT_TOKENS]..[DEDUP_MAX_OUTPUT_TOKENS].
     *
     * No headroom is added for reasoning: the stage asks for none explicitly and measures none.
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
    internal fun parseOrSalvage(raw: String, candidates: List<Article>): DedupResult {
        val strict = try {
            parseEitherShape(raw)
        } catch (e: JacksonException) {
            log.warn("[Dedup] Response did not parse ({}) — attempting to salvage the complete clusters", e.message)
            null
        }
        if (strict != null) return DedupResult(requireUsableClusters(strict.clusters))

        val salvaged = requireUsableClusters(salvageClusters(raw))
        val selected = selectArticles(salvaged, candidates).articles.size
        val required = appProperties.compose.maxArticles
        if (selected < required) {
            throw IllegalStateException(
                "Unparseable dedup response salvaged only $selected selectable article(s) from " +
                    "${salvaged.size} cluster(s), below the $required needed to compose"
            )
        }

        log.warn("[Dedup] Salvaged {} complete cluster(s) selecting {} article(s) from a truncated response — " +
            "the lost tail is beyond the compose cap of {} and cannot change the episode",
            salvaged.size, selected, required)
        return DedupResult(salvaged)
    }

    /**
     * Drops the `NEW` clusters that selected nothing, rejecting the whole response when they are the
     * majority.
     *
     * A response can parse cleanly and still be unusable. The prompt permits an empty
     * `selectedArticleIds` only for a `CONTINUATION` with no new information, so an empty `NEW`
     * cluster breaks the contract it was asked to follow. Episode 204 was composed from 1 article
     * because 33 of its 34 `NEW` clusters named a topic and selected nothing, and nothing checked a
     * parsed response the way a salvaged one is checked.
     *
     * Keying on `NEW` clusters is what separates this from the legitimate quiet day, where every
     * cluster is a `CONTINUATION` selecting nothing and zero articles is the right answer. A
     * minority of empty `NEW` clusters costs only those topics and is logged rather than failing an
     * episode over one sloppy cluster; a majority means the response is degenerate and the caller's
     * retry gets a turn.
     */
    internal fun requireUsableClusters(clusters: List<DedupCluster>): List<DedupCluster> {
        val newClusters = clusters.filter { it.status.equals(NEW_CLUSTER_STATUS, ignoreCase = true) }
        val emptyNew = newClusters.filter { it.selectedArticleIds.filterNotNull().isEmpty() }
        if (emptyNew.isEmpty()) return clusters

        if (emptyNew.size * 2 > newClusters.size) {
            throw IllegalStateException(
                "Degenerate dedup response: ${emptyNew.size} of ${newClusters.size} NEW cluster(s) " +
                    "selected no article"
            )
        }

        log.warn("[Dedup] Dropped {} of {} NEW cluster(s) that selected no article: {}",
            emptyNew.size, newClusters.size, emptyNew.joinToString(", ") { it.topic })
        return clusters - emptyNew.toSet()
    }

    /**
     * Parses the JSON inside [raw] in either shape the model answers with: the `{ "clusters": [...] }`
     * object the prompt asks for, or the bare cluster array some models emit instead. Null when [raw]
     * holds no JSON at all.
     *
     * Episode 202's dedup response was complete, valid JSON — and still cost the episode, because it
     * arrived as `**Output:**` followed by a ```json fence. Reading from the first brace or bracket
     * ignores the lead-in, and reading a single value stops at the end of that value, so the closing
     * fence and any trailing chatter are ignored without having to locate where they begin.
     */
    private fun parseEitherShape(raw: String): DedupResult? {
        val start = jsonStart(raw) ?: return null

        return jsonMapper.createParser(raw.substring(start)).use { parser ->
            if (parser.nextToken() == JsonToken.START_ARRAY) {
                DedupResult(parser.readValueAs(object : TypeReference<List<DedupCluster>>() {}))
            } else {
                parser.readValueAs(DedupResult::class.java)
            }
        }
    }

    /** Index of the first brace or bracket in [raw], or null when it holds no JSON at all. */
    private fun jsonStart(raw: String): Int? {
        val objectStart = raw.indexOf('{')
        val arrayStart = raw.indexOf('[')
        return when {
            objectStart < 0 && arrayStart < 0 -> null
            objectStart < 0 -> arrayStart
            arrayStart < 0 -> objectStart
            else -> minOf(objectStart, arrayStart)
        }
    }

    /**
     * Recovers the complete [DedupCluster] elements from a cluster array the model cut off
     * mid-element, by reading the array one element at a time and stopping where the JSON runs out.
     */
    internal fun salvageClusters(raw: String): List<DedupCluster> {
        // Only the lead-in is dropped, never the tail: a truncated response's last closer sits
        // inside the element it was cut off in, so trimming there would lose the complete elements
        // before it. Reading from the start lets the parser run out where the JSON does.
        val start = jsonStart(raw) ?: return emptyList()

        val clusters = mutableListOf<DedupCluster>()
        try {
            jsonMapper.createParser(raw.substring(start)).use { parser ->
                // A bare array is the cluster array itself; an object carries it under "clusters".
                var inClusters = parser.nextToken() == JsonToken.START_ARRAY
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

    /**
     * What the gate did, for the one line that summarises the whole stage.
     *
     * An overrule reports what the gate wanted rather than the nothing it achieved. The count
     * alone would read "0 gated out" on the run where the gate misbehaved most, and pairing that
     * with a separate warning only works for a reader who sees both lines.
     */
    internal fun describeGate(
        candidates: List<Article>,
        gatedCandidates: List<Article>,
        gate: CoveredTopicGateResult
    ): String = when {
        !gate.answered -> "ungated"
        gatedCandidates.size == candidates.size && gate.excludedIds.isNotEmpty() ->
            "gate wanted all ${gate.excludedIds.size} excluded, overruled"
        else -> "${candidates.size - gatedCandidates.size} gated out"
    }

    /**
     * The candidates to cluster: [candidates] minus what the gate excluded, unless that would be
     * all of them.
     *
     * An empty candidate list ends the run. [LlmPipeline] reads an empty filter result as "every
     * topic was already covered" and skips the episode, which on a genuinely quiet day is the
     * right answer. Letting the gate reach that state on its own would hand it an authority it
     * has not earned: a malfunction excluding everything would look exactly like a quiet day and
     * silently cost an episode, the same shape of failure as episode 204 composing from one
     * article. Deciding that nothing is left to say belongs to the clustering call, which is
     * checked for degeneracy by [requireUsableClusters].
     *
     * So a gate that excludes every candidate is treated as a gate that answered nothing. The
     * clustering call then sees the full list and may still reach the same conclusion on its own
     * evidence.
     */
    private fun keepGateFromEmptyingTheEpisode(
        candidates: List<Article>,
        gate: CoveredTopicGateResult
    ): List<Article> {
        val kept = candidates.filterNot { it.id in gate.excludedIds }
        if (kept.isNotEmpty()) return kept

        log.warn("[Dedup] The gate excluded all {} candidate(s), which it is not trusted to decide alone - " +
            "clustering them all and letting the dedup call judge", candidates.size)
        return candidates
    }

    /**
     * Returns the prompt to send on [attempt], appending a correction from the second attempt on.
     *
     * A retry must never send the byte-identical prompt. [CachingChatModel] keys on prompt text, so
     * a model that wrapped its JSON in prose has that unparseable answer cached: every retry would
     * replay it from cache and fail identically in milliseconds, which is how episode 202 burned
     * three attempts in four milliseconds each. Naming the attempt keeps every retry a real call,
     * and telling the model what went wrong makes it likelier to answer in the asked-for shape.
     */
    internal fun promptForAttempt(prompt: String, attempt: Int): String =
        if (attempt <= 1) prompt else "$prompt\n\n${jsonOnlyCorrection(attempt)}"

    private fun jsonOnlyCorrection(attempt: Int): String =
        "Retry $attempt: your previous response could not be parsed. Respond with the raw JSON " +
            "object only, in the shape { \"clusters\": [ ... ] }. Do not include reasoning, " +
            "commentary, or markdown code fences, and do not write anything before or after the JSON."

    internal fun buildPrompt(candidates: List<Article>, history: EpisodeHistory): String {
        val historicalArticles = history.articles
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

        val coveredTopicsBlock = if (history.coveredTopics.isNotEmpty()) {
            // The labels this stage itself assigned on earlier runs, most recent episode first.
            // Titles alone miss a topic whose source article was headlined about something else,
            // which is how a DeepSeek release covered the day before was composed as fresh news.
            val topics = history.coveredTopics.joinToString("\n") { "- $it" }
            """

            Topics already covered in recent episodes:
            $topics
            """
        } else ""

        return """
            You are a topic deduplication filter for a podcast pipeline. Your job is to cluster today's candidate articles by topic, compare against what recent episodes already covered, and decide what's new vs. already covered.

            For each cluster of related articles, output:
            - "topic": short label for the topic
            - "status": "NEW" (not covered in recent episodes) or "CONTINUATION" (covered before)
            - "previousContext": (CONTINUATION only) one sentence describing what was covered before
            - "selectedArticleIds": article IDs to keep for composition (max 3 per cluster)

            Rules:
            - The "Topics already covered" list is the authoritative record of what this podcast has said. A cluster matching one of those topics is a CONTINUATION even when its articles are new, from a different source, or carry a different headline.
            - A fresh analysis, technical report, benchmark or follow-up about an already-covered release is a CONTINUATION, not a NEW release. The analysis may be new; the thing it examines is not. Say so in previousContext, so the script does not announce it a second time.
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
            $coveredTopicsBlock
            $historicalBlock
        """.trimIndent()
    }

    private fun String.truncateForHistory(): String =
        if (length <= HISTORICAL_TITLE_MAX_CHARS) this else take(HISTORICAL_TITLE_MAX_CHARS).trimEnd() + "…"

}
