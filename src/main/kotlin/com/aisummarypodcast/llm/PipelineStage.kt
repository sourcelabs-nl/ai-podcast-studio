package com.aisummarypodcast.llm

import com.aisummarypodcast.config.ModelReference
import com.aisummarypodcast.config.StageDefaults

enum class PipelineStage(val value: String) {
    FILTER("filter"),
    DEDUP("dedup"),
    COMPOSE("compose");

    fun default(defaults: StageDefaults): ModelReference = when (this) {
        FILTER -> defaults.filter
        DEDUP -> defaults.dedup
        COMPOSE -> defaults.compose
    }
}
