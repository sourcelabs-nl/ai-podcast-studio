package com.aisummarypodcast.eval

/** Arithmetic over the turn indices in [ScriptJudgeAnchors]. None of it was asked of a model. */
data class AttentionScore(
    val cliffhangers: CliffhangerScore,
    val humor: HumorScore,
    val teaser: TeaserScore,
    /**
     * The mean of the three components, and the number
     * [com.aisummarypodcast.config.JudgeProperties.norm] is compared against. The equal weighting
     * is a placeholder: nothing yet says one device matters more than another. Read the components
     * when deciding what to change, this only to rank episodes against each other.
     */
    val overall: Double
)

/**
 * [deferredPromises] counts only payoffs landing at least [AttentionScoring.MIN_DEFERRAL_TURNS]
 * turns later. A promise answered in the next breath satisfies the letter of the cliffhanger rule
 * and none of its purpose, which is the failure episode 208 showed.
 */
data class CliffhangerScore(
    val promises: Int,
    val deferredPromises: Int,
    val unpaidPromises: Int,
    val medianDeferralTurns: Int?,
    val score: Double
)

/**
 * [speakerBalance] is the share of beats belonging to whichever speaker has fewer of them, so it
 * runs from 0 (one speaker makes every joke) to 0.5 (an even split).
 */
data class HumorScore(
    val beats: Int,
    val speakerBalance: Double,
    val reactionRatio: Double,
    val score: Double
)

data class TeaserScore(
    val distinctTopics: Int,
    val score: Double
)
