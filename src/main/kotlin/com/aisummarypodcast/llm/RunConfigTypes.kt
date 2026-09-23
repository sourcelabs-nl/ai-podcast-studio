package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.ModelReference
import com.aisummarypodcast.store.Podcast

/**
 * What one run changes about the configuration it would otherwise get from the podcast and the app
 * defaults. Every field left null or empty keeps the lower layer's value, so a run with no overrides
 * runs exactly as the podcast is configured.
 *
 * [researchQueryCap] replaces the web search query cap of the research stage (see
 * [com.aisummarypodcast.research.ResearchRequest.queryCap]).
 */
data class RunOverrides(
    val models: Map<PipelineStage, ModelReference> = emptyMap(),
    val reasoningEffort: Map<PipelineStage, String> = emptyMap(),
    val providerSort: String? = null,
    val preferredMinThroughput: Int? = null,
    val targetWords: Int? = null,
    val researchQueryCap: Int? = null,
    val bypassLlmCache: Boolean? = null
)

/**
 * OpenRouter provider preferences beyond the quantization floor: `provider.sort` and
 * `provider.preferred_min_throughput`. Neither is sent unless set, so the default request carries
 * only the floor (see [OpenRouterRouting.extraBodyFor]).
 */
data class ProviderPreferences(
    val sort: String? = null,
    val preferredMinThroughput: Int? = null
) {
    companion object {
        val DEFAULT = ProviderPreferences()
    }
}

/**
 * The configuration one run executes with: app defaults, then the podcast's own settings, then the
 * run's [RunOverrides], resolved once at the start of the run and passed to every stage. It is also
 * what an episode records as the configuration it was produced with.
 *
 * Every stage has a model and a reasoning effort. Compose reasons at the podcast's
 * `composeSettings.reasoningEffort` or the configured default; every other stage (and the work
 * that borrows the filter model, the research plan and the recap) runs with no reasoning.
 * [researchQueryCap] is null when the research stage keeps its own cap.
 */
data class RunConfig(
    val models: Map<PipelineStage, ModelReference>,
    val reasoningEffort: Map<PipelineStage, String>,
    val providerPreferences: ProviderPreferences,
    val targetWords: Int,
    val researchQueryCap: Int?,
    val bypassLlmCache: Boolean
) {
    fun modelFor(stage: PipelineStage): ModelReference = models.getValue(stage)

    fun reasoningEffortFor(stage: PipelineStage): String = reasoningEffort.getValue(stage)

    companion object {
        fun resolve(appProperties: AppProperties, podcast: Podcast, overrides: RunOverrides? = null): RunConfig {
            val o = overrides ?: RunOverrides()
            return RunConfig(
                models = PipelineStage.entries.associateWith { stage ->
                    o.models[stage] ?: podcast.llmModels?.get(stage.value) ?: stage.default(appProperties.llm.defaults)
                },
                reasoningEffort = PipelineStage.entries.associateWith { stage ->
                    o.reasoningEffort[stage] ?: defaultReasoningEffort(stage, podcast, appProperties)
                },
                providerPreferences = ProviderPreferences(o.providerSort, o.preferredMinThroughput),
                targetWords = o.targetWords ?: podcast.targetWords ?: appProperties.briefing.targetWords,
                researchQueryCap = o.researchQueryCap,
                bypassLlmCache = o.bypassLlmCache ?: false
            )
        }

        private fun defaultReasoningEffort(stage: PipelineStage, podcast: Podcast, appProperties: AppProperties): String =
            if (stage == PipelineStage.COMPOSE) resolveReasoningEffort(podcast, appProperties)
            else OpenRouterRouting.NO_REASONING
    }
}
