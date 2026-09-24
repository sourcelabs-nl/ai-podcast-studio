package com.aisummarypodcast.store

import com.aisummarypodcast.llm.DEDUP_GATE_STAGE
import com.aisummarypodcast.llm.GenerationStats
import com.aisummarypodcast.llm.LlmCostSource
import com.aisummarypodcast.llm.RESEARCH_PLAN_STAGE
import com.aisummarypodcast.llm.TIMEOUT_ERROR_TYPE
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.sql.ResultSet

/**
 * Which rows count as measured request latency: a real request that either answered or ran out of
 * time. See the note on [LlmCallRepositoryCustomImpl] for why a fast failure does not qualify.
 */
private const val QUALIFIES_FOR_LATENCY =
    "cache_hit = 0 AND (outcome = 'ok' OR error_type = '$TIMEOUT_ERROR_TYPE')"

/**
 * Which requests belong to one episode: those issued for the episode itself, and those issued for
 * an article it scored as a candidate.
 *
 * The second half is the scoring stage. Its requests are issued when an article arrives, days
 * before the episode that uses it exists, so they name an article and no episode. Without them an
 * episode reports a scoring stage that issued nothing. An article stands as a candidate for exactly
 * one episode (windows do not overlap), so no request is claimed twice.
 */
private const val BELONGS_TO_EPISODE =
    "(episode_id = :episodeId OR article_id IN " +
        "(SELECT article_id FROM episode_candidate_articles WHERE episode_id = :episodeId))"

/**
 * The stage names the cost breakdown uses, which are not the stage names the requests are recorded
 * under. Scoring and recap both run on the podcast's filter model and both record themselves as
 * `filter`, but the breakdown bills them separately, so they are told apart by what the request was
 * issued for: a scoring request names the article it scored, a recap request names its episode.
 */
object CostStage {
    const val SCORE = "score"
    const val RECAP = "recap"
    const val DEDUP = "dedup"
    const val COMPOSE = "compose"
    const val GATE = DEDUP_GATE_STAGE

    /**
     * The research plan. Not in [ALL]: it has no persisted episode columns, so it is only ever read
     * from the log, and the breakdown adds it to the research row.
     */
    const val RESEARCH_PLAN = RESEARCH_PLAN_STAGE

    /** Every stage an episode is billed for. A stage added above belongs here too. */
    val ALL = setOf(SCORE, RECAP, DEDUP, COMPOSE, GATE)
}

/**
 * One stage of one episode, totalled over the requests recorded for it.
 *
 * [calls] counts every request that belongs to the episode, including cached and failed ones, so
 * this figure and the episode's request list cannot report different numbers.
 *
 * [costUsd] and [unresolvedCalls] cover the requests that succeeded. A request that failed reported
 * no tokens and was not charged, so counting it as a cost that could not be resolved would gate a
 * stage off its projection for having retried once.
 *
 * [unresolvedCalls] is what makes the projection safe to trust: a stage holding a request whose cost
 * was never resolved (every row written before V76) understates what the stage cost, and reports its
 * persisted column instead.
 */
data class LlmStageTotals(
    val stage: String,
    val calls: Int,
    val inputTokens: Int,
    val outputTokens: Int,
    val costUsd: Double?,
    val unresolvedCalls: Int,
    val sources: List<LlmCostSource>
)

/** Latency percentiles for one stage, over the rows that qualify as measured request latency. */
data class LlmCallLatency(
    val stage: String,
    val samples: Int,
    val p50Ms: Long?,
    val p90Ms: Long?,
    val p95Ms: Long?,
    val p99Ms: Long?
)

/** One recorded request, as listed for a single episode. */
data class LlmCallRow(
    val startedAt: String,
    val stage: String,
    val model: String,
    val durationMs: Long,
    val outcome: String,
    val cacheHit: Boolean,
    val servedProvider: String? = null,
    val reasoningTokens: Int? = null,
    /** OpenRouter's account of the request; null until it has been fetched, and for every other provider. */
    val generationStats: GenerationStats? = null
)

/**
 * Which requests a latency read covers.
 *
 * An [episodeId] and a [cutoff] are alternatives rather than filters that combine. An episode is a
 * bounded set of requests rather than a period, so intersecting it with a window could only hide
 * some of its requests and would make the answer depend on when the page was opened.
 */
data class LlmCallScope(
    val cutoff: String? = null,
    val episodeId: Long? = null
)

interface LlmCallRepositoryCustom {
    fun latencyPercentiles(scope: LlmCallScope): List<LlmCallLatency>

    /** Every recorded request of one episode, newest first, including cached and failed ones. */
    fun requestsForEpisode(episodeId: Long): List<LlmCallRow>

    /**
     * The start time of the earliest request that names an episode, or null when no request does.
     *
     * This is what separates an episode that issued nothing from one generated before requests
     * carried an episode at all: below this point no row could have been attributed. Derived rather
     * than configured, so there is no deploy-time constant to keep correct.
     */
    fun earliestAttributedStart(): String?

    /**
     * Per-stage totals over the requests belonging to one episode, keyed by [CostStage].
     *
     * The judge is left out: it runs over a finished script rather than producing one, and the
     * episode is not billed for it.
     */
    fun stageTotalsForEpisode(episodeId: Long): List<LlmStageTotals>

    /**
     * How many of an episode's candidate articles have a scoring request attributed to them, and how
     * many it recorded. The score stage may only be projected when the two are equal: scoring runs
     * before the episode exists and reaches it only through its candidates, so a partial log
     * understates the most expensive stage of the pipeline rather than merely failing to improve it.
     */
    fun scoreAttribution(episodeId: Long): ScoreAttribution

    /**
     * Writes OpenRouter's account of the request recorded as [id]. The served provider is only
     * filled in where the response itself did not report one.
     */
    fun updateGenerationStats(id: Long, stats: GenerationStats)
}

/** How much of an episode's scoring the request log can account for. See [LlmCallRepositoryCustom.scoreAttribution]. */
data class ScoreAttribution(val candidates: Int, val attributed: Int) {
    val isComplete: Boolean get() = candidates > 0 && attributed == candidates
}

/**
 * Percentiles are computed in SQL by offsetting into the ordered durations rather than by loading
 * rows into the JVM, and they are exact rather than sketched: at this table's volume an exact answer
 * is affordable, and a timeout decision should not rest on an approximation nobody can check.
 *
 * A non-cached row qualifies for the percentiles when it succeeded or when it ran out of time. A
 * cache hit performed no request. A call that failed quickly reports how fast the provider refused
 * rather than how long it takes to answer, and counting it would pull the percentiles down exactly
 * when the provider is unhealthy: the endpoint behind the dedup gate returns `529 system_overloaded`
 * in milliseconds. A timeout is the opposite case, a real request that was genuinely too slow, and
 * dropping it would hide the slowest requests from the percentiles that exist to show them. The
 * consequence is that a saturated stage reads as a p99 at or near its configured timeout, which is
 * the honest reading: that is the longest a request is allowed to take.
 *
 * The per-episode request list includes every row, because it describes what the episode did rather
 * than what the provider's latency was.
 */
@Repository
class LlmCallRepositoryCustomImpl(
    private val jdbcClient: JdbcClient,
    private val jsonMapper: JsonMapper
) : LlmCallRepositoryCustom {

    override fun latencyPercentiles(scope: LlmCallScope): List<LlmCallLatency> {
        val stages = jdbcClient.sql(
            """
            SELECT stage, COUNT(*) AS samples
            FROM llm_calls
            WHERE $QUALIFIES_FOR_LATENCY ${scope.sqlCondition()}
            GROUP BY stage
            ORDER BY stage
            """.trimIndent()
        )
            .bind(scope)
            .query { rs, _ -> rs.getString("stage") to rs.getInt("samples") }
            .list()

        return stages.map { (stage, samples) ->
            LlmCallLatency(
                stage = stage,
                samples = samples,
                p50Ms = percentile(stage, scope, samples, 50),
                p90Ms = percentile(stage, scope, samples, 90),
                p95Ms = percentile(stage, scope, samples, 95),
                p99Ms = percentile(stage, scope, samples, 99)
            )
        }
    }

    override fun requestsForEpisode(episodeId: Long): List<LlmCallRow> =
        jdbcClient.sql(
            """
            SELECT started_at, stage, model, duration_ms, outcome, cache_hit, served_provider, reasoning_tokens,
                   first_content_ms, generation_time_ms, native_completion_tokens, native_reasoning_tokens,
                   finish_reason, provider_attempts_json
            FROM llm_calls
            WHERE $BELONGS_TO_EPISODE
            ORDER BY started_at DESC, id DESC
            """.trimIndent()
        )
            .param("episodeId", episodeId)
            .query { rs, _ ->
                LlmCallRow(
                    startedAt = rs.getString("started_at"),
                    stage = rs.getString("stage"),
                    model = rs.getString("model"),
                    durationMs = rs.getLong("duration_ms"),
                    outcome = rs.getString("outcome"),
                    cacheHit = rs.getBoolean("cache_hit"),
                    servedProvider = rs.getString("served_provider"),
                    reasoningTokens = rs.getInt("reasoning_tokens").takeUnless { rs.wasNull() },
                    generationStats = generationStatsOf(rs)
                )
            }
            .list()

    override fun updateGenerationStats(id: Long, stats: GenerationStats) {
        jdbcClient.sql(
            """
            UPDATE llm_calls
            SET first_content_ms = :firstContentMs,
                generation_time_ms = :generationTimeMs,
                native_completion_tokens = :nativeCompletionTokens,
                native_reasoning_tokens = :nativeReasoningTokens,
                finish_reason = :finishReason,
                provider_attempts_json = :attempts,
                served_provider = COALESCE(served_provider, :servedProvider)
            WHERE id = :id
            """.trimIndent()
        )
            .param("firstContentMs", stats.firstContentMs)
            .param("generationTimeMs", stats.generationTimeMs)
            .param("nativeCompletionTokens", stats.nativeCompletionTokens)
            .param("nativeReasoningTokens", stats.nativeReasoningTokens)
            .param("finishReason", stats.finishReason)
            .param("attempts", jsonMapper.writeValueAsString(stats.attempts))
            .param("servedProvider", stats.servedProvider)
            .param("id", id)
            .update()
    }

    /** Stats are present once a lookup has written the attempts, which every successful lookup does. */
    private fun generationStatsOf(rs: ResultSet): GenerationStats? {
        val attemptsJson = rs.getString("provider_attempts_json") ?: return null
        return GenerationStats(
            firstContentMs = rs.getLong("first_content_ms").takeUnless { rs.wasNull() },
            generationTimeMs = rs.getLong("generation_time_ms").takeUnless { rs.wasNull() },
            nativeCompletionTokens = rs.getInt("native_completion_tokens").takeUnless { rs.wasNull() },
            nativeReasoningTokens = rs.getInt("native_reasoning_tokens").takeUnless { rs.wasNull() },
            finishReason = rs.getString("finish_reason"),
            servedProvider = rs.getString("served_provider"),
            attempts = jsonMapper.readValue(attemptsJson)
        )
    }

    override fun earliestAttributedStart(): String? =
        jdbcClient.sql("SELECT MIN(started_at) FROM llm_calls WHERE episode_id IS NOT NULL")
            .query(String::class.java)
            .optional()
            .orElse(null)

    override fun stageTotalsForEpisode(episodeId: Long): List<LlmStageTotals> =
        jdbcClient.sql(
            """
            SELECT CASE
                       WHEN stage = 'filter' AND article_id IS NOT NULL THEN '${CostStage.SCORE}'
                       WHEN stage = 'filter' THEN '${CostStage.RECAP}'
                       ELSE stage
                   END AS cost_stage,
                   COUNT(*) AS calls,
                   SUM(input_tokens) AS input_tokens,
                   SUM(output_tokens) AS output_tokens,
                   SUM(CASE WHEN outcome = 'ok' THEN resolved_cost_usd END) AS cost_usd,
                   SUM(CASE WHEN outcome = 'ok' AND resolved_cost_usd IS NULL THEN 1 ELSE 0 END) AS unresolved_calls,
                   GROUP_CONCAT(DISTINCT cost_source) AS sources
            FROM llm_calls
            WHERE $BELONGS_TO_EPISODE AND stage <> 'eval'
            GROUP BY cost_stage
            """.trimIndent()
        )
            .param("episodeId", episodeId)
            .query { rs, _ ->
                LlmStageTotals(
                    stage = rs.getString("cost_stage"),
                    calls = rs.getInt("calls"),
                    inputTokens = rs.getInt("input_tokens"),
                    outputTokens = rs.getInt("output_tokens"),
                    costUsd = rs.getDouble("cost_usd").takeUnless { rs.wasNull() },
                    unresolvedCalls = rs.getInt("unresolved_calls"),
                    sources = rs.getString("sources").orEmpty()
                        .split(",")
                        .filter { it.isNotBlank() }
                        .mapNotNull { name -> LlmCostSource.entries.firstOrNull { it.name == name } }
                )
            }
            .list()

    override fun scoreAttribution(episodeId: Long): ScoreAttribution =
        jdbcClient.sql(
            """
            SELECT COUNT(*) AS candidates,
                   SUM(
                       CASE WHEN EXISTS (
                           SELECT 1 FROM llm_calls c
                           WHERE c.article_id = e.article_id AND c.stage = 'filter'
                       ) THEN 1 ELSE 0 END
                   ) AS attributed
            FROM episode_candidate_articles e
            WHERE e.episode_id = :episodeId
            """.trimIndent()
        )
            .param("episodeId", episodeId)
            .query { rs, _ -> ScoreAttribution(rs.getInt("candidates"), rs.getInt("attributed")) }
            .single()

    /**
     * Nearest-rank: the smallest duration at or below which [percentile] percent of the samples fall.
     * The rank is clamped to the last row so that p99 of a handful of samples is the slowest of them
     * rather than an offset past the end. For one episode that is the common case rather than an
     * edge: a stage issues a handful of requests, so p99 is the slowest observed one.
     */
    private fun percentile(stage: String, scope: LlmCallScope, samples: Int, percentile: Int): Long? {
        if (samples == 0) return null
        val rank = Math.ceil(samples * percentile / 100.0).toInt().coerceIn(1, samples)
        return jdbcClient.sql(
            """
            SELECT duration_ms
            FROM llm_calls
            WHERE $QUALIFIES_FOR_LATENCY AND stage = :stage ${scope.sqlCondition()}
            ORDER BY duration_ms
            LIMIT 1 OFFSET :offset
            """.trimIndent()
        )
            .param("stage", stage)
            .param("offset", rank - 1)
            .bind(scope)
            .query(Long::class.java)
            .optional()
            .orElse(null)
    }

    private fun LlmCallScope.sqlCondition(): String =
        if (episodeId != null) "AND $BELONGS_TO_EPISODE" else "AND started_at >= :cutoff"

    private fun JdbcClient.StatementSpec.bind(scope: LlmCallScope): JdbcClient.StatementSpec =
        if (scope.episodeId != null) param("episodeId", scope.episodeId) else param("cutoff", scope.cutoff)
}
