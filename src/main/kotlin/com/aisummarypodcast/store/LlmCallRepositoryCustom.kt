package com.aisummarypodcast.store

import com.aisummarypodcast.llm.TIMEOUT_ERROR_TYPE
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

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
    val cacheHit: Boolean
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
    private val jdbcClient: JdbcClient
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
            SELECT started_at, stage, model, duration_ms, outcome, cache_hit
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
                    cacheHit = rs.getBoolean("cache_hit")
                )
            }
            .list()

    override fun earliestAttributedStart(): String? =
        jdbcClient.sql("SELECT MIN(started_at) FROM llm_calls WHERE episode_id IS NOT NULL")
            .query(String::class.java)
            .optional()
            .orElse(null)

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
