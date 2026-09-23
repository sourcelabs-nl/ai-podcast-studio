package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.ModelCost
import org.springframework.stereotype.Component

/**
 * A model resolved for one pipeline stage. [stage] travels with the model so downstream code —
 * notably the per-stage request timeout in [ChatClientFactory] — does not have to be told the
 * stage a second time.
 *
 * [telemetryStage] is the stage name the requests are recorded under. It is the stage's own name
 * unless a caller borrows a stage's model for work of its own, as the research plan borrows the
 * filter model and records as [RESEARCH_PLAN_STAGE] so its requests are not read as scoring.
 *
 * [reasoningEffort] and [providerPreferences] are the run's settings for this stage, so a request
 * built from the model carries them without the caller resolving them again (see
 * [withRoutingAndReasoning]).
 */
data class ResolvedModel(
    val provider: String,
    val model: String,
    val cost: ModelCost?,
    val stage: PipelineStage,
    val telemetryStage: String = stage.value,
    val reasoningEffort: String = OpenRouterRouting.NO_REASONING,
    val providerPreferences: ProviderPreferences = ProviderPreferences.DEFAULT
)

@Component
class ModelResolver(
    private val appProperties: AppProperties
) {

    /** The model, reasoning effort and provider preferences [runConfig] gives [stage]. */
    fun resolve(runConfig: RunConfig, stage: PipelineStage): ResolvedModel {
        val ref = runConfig.modelFor(stage)
        return ResolvedModel(
            provider = ref.provider,
            model = ref.model,
            cost = appProperties.models[ref.provider]?.get(ref.model),
            stage = stage,
            reasoningEffort = runConfig.reasoningEffortFor(stage),
            providerPreferences = runConfig.providerPreferences
        )
    }
}
