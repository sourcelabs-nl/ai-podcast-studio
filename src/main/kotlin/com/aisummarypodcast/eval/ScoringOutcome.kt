package com.aisummarypodcast.eval

import com.aisummarypodcast.config.JudgeMode
import com.aisummarypodcast.store.EpisodeScore

/**
 * One episode's scoring result, together with what the configured mode decided to do about it.
 */
data class ScoringOutcome(
    val score: EpisodeScore,
    val decision: JudgeDecision
)

/**
 * What the run concluded from a score.
 *
 * [norm] is null whenever none is configured. In that case [mode] may still be
 * [JudgeMode.ENFORCE] while [belowNorm] is false and [reason] says the norm was absent: the mode
 * was asked for, and the run declined to act because it had nothing to act against.
 */
data class JudgeDecision(
    val mode: JudgeMode,
    val norm: Double?,
    val belowNorm: Boolean,
    val reason: String
)
