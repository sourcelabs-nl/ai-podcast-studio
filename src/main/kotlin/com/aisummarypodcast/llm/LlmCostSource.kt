package com.aisummarypodcast.llm

/**
 * Where an LLM cost figure came from. Persisted per episode (`episodes.llm_cost_source`) so an
 * actual provider charge is distinguishable from an estimate derived from the configured
 * per-Mtok rates.
 */
enum class LlmCostSource {
    /** Reported by the provider on a live call (OpenRouter returns `cost` in its usage object). */
    API,

    /** A provider-reported cost replayed from the LLM cache rather than freshly charged. */
    API_CACHED,

    /** Computed from the configured `input-cost-per-mtok` / `output-cost-per-mtok` rates. */
    TABLE,

    /** Some contributing calls reported a cost and some were estimated from the rates. */
    MIXED,

    /** Neither a reported cost nor configured rates were available. */
    UNKNOWN;

    companion object {
        private val REPORTED = setOf(API, API_CACHED)

        /**
         * Combines the sources of an episode's contributing stages into a single aggregate.
         * Returns null for an empty list, so an episode with no contributing stage keeps a null
         * source — the same value legacy episodes carry.
         *
         * A mix of [API] and [API_CACHED] aggregates to [API]: both are provider-reported values,
         * so the total stays authoritative even though part of it was replayed from the cache.
         */
        fun aggregate(sources: List<LlmCostSource>): LlmCostSource? {
            if (sources.isEmpty()) return null
            val distinct = sources.distinct()
            if (distinct.size == 1) return distinct.single()
            if (MIXED in distinct) return MIXED
            val reported = distinct.any { it in REPORTED }
            val estimated = distinct.any { it == TABLE || it == UNKNOWN }
            return when {
                reported && estimated -> MIXED
                reported -> API
                else -> TABLE
            }
        }
    }
}
