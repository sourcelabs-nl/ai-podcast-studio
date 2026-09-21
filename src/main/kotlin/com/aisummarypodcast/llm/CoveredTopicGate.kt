package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.DedupGateProperties
import com.aisummarypodcast.store.Article
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * The proposition put to Jev about each candidate.
 *
 * The clauses after the first are not padding. Without them the model reads "about a topic that
 * appears in `covered_topics`" narrowly and lets through exactly what this stage exists to catch:
 * the same story from another outlet, under another headline, or a benchmark of something already
 * announced. They restate the rule the clustering prompt already gives in prose.
 */
private const val ALREADY_COVERED_INSTRUCTIONS =
    "The candidate in `candidates.%s` is about a topic that already appears in `covered_topics`. " +
        "It counts as already covered even when the article is new, from a different source, " +
        "carries a different headline, or is a fresh analysis, benchmark or follow-up about the " +
        "already-covered thing."

/** Prefix keeping a question key a valid identifier once an article id is appended. */
private const val QUESTION_KEY_PREFIX = "a"

/** Rough characters per candidate beyond its title and summary: the key, the instructions, JSON. */
private const val CANDIDATE_OVERHEAD_CHARS = 400

/**
 * What the gate decided, and what it cost.
 *
 * [answered] is false when the gate did not run or returned nothing, which is the case every
 * caller must treat as "no candidate is covered". It is distinct from an empty [excludedIds],
 * which is the gate working and finding nothing already covered.
 *
 * [inputTokens], [reportedCostUsd] and [requests] are the gate's own spend, reported apart from the
 * clustering call's so the two can be told apart wherever an episode's cost is read. [requests]
 * counts HTTP attempts across every chunk, including a batch that ended up answering nothing: those
 * attempts reached the provider and were charged for.
 */
data class CoveredTopicGateResult(
    val excludedIds: Set<Long>,
    val reportedCostUsd: Double?,
    val answered: Boolean,
    val inputTokens: Int = 0,
    val requests: Int = 0
) {
    companion object {
        val NOT_RUN = CoveredTopicGateResult(emptySet(), null, answered = false)
    }
}

/**
 * Decides which dedup candidates are about a topic recent episodes already covered, so the
 * clustering call never has to see them.
 *
 * This is the one closed decision in the dedup stage. Clustering is a partition with no fixed
 * label set, and the per-cluster selection and `previousContext` depend on it, so those stay in
 * the generative call. Taking only this decision out moves it into a call that cannot answer with
 * prose, an unparseable payload or a truncated array, which is how episodes 191, 200, 202 and 204
 * were lost.
 *
 * Measured over 60 candidates, 20 of them drawn from the previous episode and so provably already
 * covered: 857ms and $0.00074 against the clustering call's 22.9s and $0.00354, catching all 20
 * and flagging the same 4 of 40 fresh candidates the clustering call flags. Every figure is in
 * `knowledge/references/jev-decisions-endpoint.md`.
 */
@Component
class CoveredTopicGate(
    private val jevClient: JevClient,
    appProperties: AppProperties
) {

    private val properties: DedupGateProperties = appProperties.llm.dedup.gate
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * [episodeId] names the episode this runs for, so the gate's own requests appear in that
     * episode's request list. It is null on the preview path, which deduplicates before any episode
     * exists.
     */
    fun evaluate(
        candidates: List<Article>,
        coveredTopics: List<String>,
        userId: String,
        episodeId: Long? = null
    ): CoveredTopicGateResult {
        if (!properties.enabled) return CoveredTopicGateResult.NOT_RUN
        if (candidates.isEmpty()) return CoveredTopicGateResult.NOT_RUN
        // Nothing has been covered, so nothing can repeat it. Asking would spend a call to be told
        // so, and would put an empty list in front of a model that has to judge against it.
        if (coveredTopics.isEmpty()) return CoveredTopicGateResult.NOT_RUN

        val endpoint = JevEndpoint(properties.url, properties.model)
        val excluded = mutableSetOf<Long>()
        var cost: Double? = null
        var anyAnswer = false
        var inputTokens = 0
        var requests = 0

        for (chunk in chunkCandidates(candidates, coveredTopics)) {
            val answers = jevClient.ask(
                userId = userId,
                state = mapOf(
                    "covered_topics" to coveredTopics,
                    "candidates" to chunk.associate { it.id.toString() to candidateState(it) }
                ),
                questions = chunk.associate {
                    questionKey(it.id!!) to JevNoulQuestion(ALREADY_COVERED_INSTRUCTIONS.format(it.id))
                },
                endpoint = endpoint,
                caller = JevCaller(stage = DEDUP_GATE_STAGE, episodeId = episodeId)
            )
            // Counted before the answers are looked at: a chunk that failed still cost its
            // attempts, and dropping them here would report the gate as cheaper the worse it ran.
            requests += answers.requests
            inputTokens += answers.inputTokens
            answers.reportedCostUsd?.let { cost = (cost ?: 0.0) + it }

            if (answers.noul.isEmpty()) continue

            anyAnswer = true
            for (article in chunk) {
                val id = article.id ?: continue
                val value = answers.noul[questionKey(id)] ?: continue
                if (value >= properties.threshold) excluded.add(id)
            }
        }

        if (!anyAnswer) {
            log.warn("[Dedup] Already-covered gate returned nothing for {} candidate(s), clustering them all", candidates.size)
            // The spend is still reported. The gate answered nothing, but its attempts were made
            // and charged, and an episode that paid for them should say so.
            return CoveredTopicGateResult.NOT_RUN.copy(
                reportedCostUsd = cost,
                inputTokens = inputTokens,
                requests = requests
            )
        }

        log.info("[Dedup] Already-covered gate excluded {} of {} candidate(s) against {} covered topic(s)",
            excluded.size, candidates.size, coveredTopics.size)
        return CoveredTopicGateResult(excluded, cost, answered = true, inputTokens = inputTokens, requests = requests)
    }

    /** The question key for [articleId], which is also how an answer is found again. */
    private fun questionKey(articleId: Long): String = "$QUESTION_KEY_PREFIX$articleId"

    private fun candidateState(article: Article): Map<String, String> = mapOf(
        "title" to article.title,
        "summary" to (article.summary ?: article.body).take(properties.summaryMaxChars)
    )

    /**
     * Splits [candidates] into batches whose request stays under the configured character budget.
     *
     * The endpoint rejects on request size, not on question count, so no fixed number of
     * candidates is safe: 160 candidates of 800-character summaries were accepted at 36,924 input
     * tokens and 180 were not, while the same 240 at 300 characters fitted in 35,648. Each chunk
     * repeats the covered topics, which is the price of splitting and the reason the budget sits
     * near the measured ceiling rather than comfortably below it.
     *
     * The chunks are of even size rather than filled greedily. Every chunk repeats the covered
     * topics, so a greedy fill leaves a tail chunk that pays that fixed cost for whatever few
     * candidates are left over: a real run of 186 candidates against 190 covered topics split
     * 184 and 2, and the second request carried the whole topic list to ask about two articles.
     * Four chunks of 47 cost the same as 184 and 2 and leave every request comfortably inside the
     * ceiling.
     *
     * A single candidate too large to fit alone is still sent alone rather than dropped: the
     * endpoint's rejection is an ordinary failure that leaves it unanswered, and an unanswered
     * candidate is clustered, which is the safe outcome.
     */
    internal fun chunkCandidates(candidates: List<Article>, coveredTopics: List<String>): List<List<Article>> {
        val fixedChars = coveredTopics.sumOf { it.length + 4 }
        val budget = properties.maxRequestChars - fixedChars
        if (budget <= 0) {
            log.warn("[Dedup] Covered topics alone exceed the gate's {} character budget, asking nothing",
                properties.maxRequestChars)
            return emptyList()
        }

        val candidateChars = candidates.sumOf {
            it.title.length + properties.summaryMaxChars + CANDIDATE_OVERHEAD_CHARS
        }
        // Ceiling division, so the last chunk is never the only one carrying the remainder.
        val chunkCount = ((candidateChars + budget - 1) / budget).coerceAtLeast(1)
        val perChunk = ((candidates.size + chunkCount - 1) / chunkCount).coerceAtLeast(1)
        return candidates.chunked(perChunk)
    }
}
