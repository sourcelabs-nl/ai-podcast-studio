package com.aisummarypodcast.store

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

/** Latency percentiles for one stage, over the rows that qualify as measured request latency. */
data class LlmCallLatency(
    val stage: String,
    val samples: Int,
    val p50Ms: Long?,
    val p90Ms: Long?,
    val p95Ms: Long?,
    val p99Ms: Long?
)

interface LlmCallRepositoryCustom {
    fun latencyPercentilesSince(cutoff: String): List<LlmCallLatency>
}

/**
 * Percentiles are computed in SQL by offsetting into the ordered durations rather than by loading
 * rows into the JVM, and they are exact rather than sketched: at this table's volume an exact answer
 * is affordable, and a timeout decision should not rest on an approximation nobody can check.
 *
 * Only successful, non-cached rows qualify. A cache hit performed no request, and a failed call
 * reports the time until it failed, which for a timeout is the timeout itself: counting either would
 * describe the application rather than the provider.
 */
@Repository
class LlmCallRepositoryCustomImpl(
    private val jdbcClient: JdbcClient
) : LlmCallRepositoryCustom {

    override fun latencyPercentilesSince(cutoff: String): List<LlmCallLatency> {
        val stages = jdbcClient.sql(
            """
            SELECT stage, COUNT(*) AS samples
            FROM llm_calls
            WHERE started_at >= :cutoff AND cache_hit = 0 AND outcome = 'ok'
            GROUP BY stage
            ORDER BY stage
            """.trimIndent()
        )
            .param("cutoff", cutoff)
            .query { rs, _ -> rs.getString("stage") to rs.getInt("samples") }
            .list()

        return stages.map { (stage, samples) ->
            LlmCallLatency(
                stage = stage,
                samples = samples,
                p50Ms = percentile(stage, cutoff, samples, 50),
                p90Ms = percentile(stage, cutoff, samples, 90),
                p95Ms = percentile(stage, cutoff, samples, 95),
                p99Ms = percentile(stage, cutoff, samples, 99)
            )
        }
    }

    /**
     * Nearest-rank: the smallest duration at or below which [percentile] percent of the samples fall.
     * The rank is clamped to the last row so that p99 of a handful of samples is the slowest of them
     * rather than an offset past the end.
     */
    private fun percentile(stage: String, cutoff: String, samples: Int, percentile: Int): Long? {
        if (samples == 0) return null
        val rank = Math.ceil(samples * percentile / 100.0).toInt().coerceIn(1, samples)
        return jdbcClient.sql(
            """
            SELECT duration_ms
            FROM llm_calls
            WHERE started_at >= :cutoff AND cache_hit = 0 AND outcome = 'ok' AND stage = :stage
            ORDER BY duration_ms
            LIMIT 1 OFFSET :offset
            """.trimIndent()
        )
            .param("cutoff", cutoff)
            .param("stage", stage)
            .param("offset", rank - 1)
            .query(Long::class.java)
            .optional()
            .orElse(null)
    }
}
