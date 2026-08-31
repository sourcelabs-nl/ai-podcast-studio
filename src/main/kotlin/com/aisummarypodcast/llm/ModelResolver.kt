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
 */
data class ResolvedModel(
    val provider: String,
    val model: String,
    val cost: ModelCost?,
    val stage: PipelineStage
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
