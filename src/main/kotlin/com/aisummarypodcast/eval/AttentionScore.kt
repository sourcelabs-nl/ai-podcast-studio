package com.aisummarypodcast.eval

/**
 * The figures derived from [ScriptJudgeAnchors]. Every value here is arithmetic over turn indices;
 * none of it was asked of a model.
 */
data class AttentionScore(
    val cliffhangers: CliffhangerScore,
    val humor: HumorScore,
    val teaser: TeaserScore,
    /**
     * The mean of the three component scores, each in `[0, 1]`.
     *
     * This is the number [com.aisummarypodcast.config.JudgeProperties.norm] is compared against. It
     * is a convenience over the components, not a measurement in its own right: the weighting is
     * an equal split chosen because nothing yet says one device matters more than another. Read the
     * components when deciding what to change; read this only to rank episodes against each other.
     */
    val overall: Double
)

/**
 * [deferredPromises] counts promises whose payoff lands at least [AttentionScoring.MIN_DEFERRAL_TURNS]
 * turns later. A promise answered in the next breath satisfies the letter of the cliffhanger rule
 * and none of its purpose, which is the failure episode 208 showed, so it is counted separately
 * from [promises].
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
