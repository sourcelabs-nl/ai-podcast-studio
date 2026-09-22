package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import java.time.Duration

/**
 * The stage name the dedup stage's already-covered gate records its requests under.
 *
 * Deliberately not `dedup`. The clustering call runs about 23s and the gate about 0.6s, so folding
 * them into one set of percentiles produces a p50 that describes neither, and telling them apart is
 * the whole reason the gate is recorded at all.
 */
const val DEDUP_GATE_STAGE = "dedup-gate"

/**
 * The stage name the pre-compose research plan records its requests under. It runs on the filter
 * model, but it is neither scoring nor recap, and the cost breakdown bills it to research.
 */
const val RESEARCH_PLAN_STAGE = "research-plan"

/**
 * A stage the latency report covers, and the request timeout its calls are issued with.
 *
 * This exists because the reported stages are not the same set as [PipelineStage]. That enum also
 * answers which model a stage resolves to and which configured timeout it uses, and the dedup gate
 * has neither: it is not part of producing an episode, and its model and timeout come from
 * `app.llm.dedup.gate`. Giving it an enum constant would force it to answer questions that have no
 * answer for it, so the report keeps its own list instead.
 *
 * A stage absent from this list is invisible in the report however many requests it issued, which is
 * indistinguishable from having issued none. Anything that records requests belongs here.
 */
data class ReportedStage(val stage: String, val timeout: Duration) {

    companion object {
        /**
         * Every stage the latency report covers: the pipeline's own, plus the callers that record
         * requests without being a pipeline stage.
         */
        fun all(properties: AppProperties): List<ReportedStage> =
            PipelineStage.entries.map { ReportedStage(it.value, it.timeout(properties.llm.timeouts)) } +
                ReportedStage(DEDUP_GATE_STAGE, properties.llm.dedup.gate.timeout) +
                ReportedStage(RESEARCH_PLAN_STAGE, PipelineStage.FILTER.timeout(properties.llm.timeouts))
    }
}
