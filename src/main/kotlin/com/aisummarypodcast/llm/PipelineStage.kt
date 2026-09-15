package com.aisummarypodcast.llm

import com.aisummarypodcast.config.ModelReference
import com.aisummarypodcast.config.StageDefaults
import com.aisummarypodcast.config.StageTimeouts
import java.time.Duration

enum class PipelineStage(val value: String) {
    FILTER("filter"),
    DEDUP("dedup"),
    COMPOSE("compose"),

    /**
     * The script judge. Not part of producing an episode: it runs afterwards, over a script that
     * already exists, so its model and its cost are resolved and tracked separately from the
     * stages the episode is billed for.
     */
    EVAL("eval");

    fun default(defaults: StageDefaults): ModelReference = when (this) {
        FILTER -> defaults.filter
        DEDUP -> defaults.dedup
        COMPOSE -> defaults.compose
        EVAL -> defaults.eval
    }

    /**
     * The request timeout this stage's calls are issued with. Lives here so that the client that
     * applies it and the telemetry that reports latency against it cannot drift apart.
     */
    fun timeout(timeouts: StageTimeouts): Duration = when (this) {
        FILTER -> timeouts.filter
        DEDUP -> timeouts.dedup
        COMPOSE -> timeouts.compose
        EVAL -> timeouts.eval
    }
}
