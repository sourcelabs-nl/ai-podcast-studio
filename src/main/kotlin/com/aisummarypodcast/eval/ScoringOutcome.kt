package com.aisummarypodcast.eval

import com.aisummarypodcast.config.JudgeMode
import com.aisummarypodcast.store.EpisodeScore

data class ScoringOutcome(
    val score: EpisodeScore,
    val decision: JudgeDecision
)

/**
 * With no [norm] configured, [mode] may still read [JudgeMode.ENFORCE] while [belowNorm] is false
 * and [reason] says the norm was absent: the mode was asked for, and the run declined to act
 * because it had nothing to act against.
 */
data class JudgeDecision(
    val mode: JudgeMode,
    val norm: Double?,
    val belowNorm: Boolean,
    val reason: String
)
