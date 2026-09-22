package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.ModelCost
import com.aisummarypodcast.config.ModelReference
import com.aisummarypodcast.store.Podcast
import org.springframework.stereotype.Component

/**
 * A model resolved for one pipeline stage. [stage] travels with the model so downstream code —
 * notably the per-stage request timeout in [ChatClientFactory] — does not have to be told the
 * stage a second time.
 *
 * [telemetryStage] is the stage name the requests are recorded under. It is the stage's own name
 * unless a caller borrows a stage's model for work of its own, as the research plan borrows the
 * filter model and records as [RESEARCH_PLAN_STAGE] so its requests are not read as scoring.
 */
data class ResolvedModel(
    val provider: String,
    val model: String,
    val cost: ModelCost?,
    val stage: PipelineStage,
    val telemetryStage: String = stage.value
)

@Component
class ModelResolver(
    private val appProperties: AppProperties
) {

    fun resolve(podcast: Podcast, stage: PipelineStage): ResolvedModel {
        val ref = podcast.llmModels?.get(stage.value)
            ?: stage.default(appProperties.llm.defaults)

        val cost = appProperties.models[ref.provider]?.get(ref.model)

        return ResolvedModel(
            provider = ref.provider,
            model = ref.model,
            cost = cost,
            stage = stage
        )
    }
}
