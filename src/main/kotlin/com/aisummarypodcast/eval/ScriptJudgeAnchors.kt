package com.aisummarypodcast.eval

/**
 * What the judge returns: positions in the script, never measurements.
 *
 * Every field can be checked by opening the script at the named turn and looking. A count or a
 * rating asked of the model could not be checked and would drift between judge model versions with
 * nothing to notice, so [AttentionScoring] derives the figures from these indices instead.
 */
data class ScriptJudgeAnchors(
    val promises: List<PromiseAnchor> = emptyList(),
    val humorBeats: List<HumorAnchor> = emptyList(),
    val teaserTopics: List<String> = emptyList()
)

/**
 * A null [payoffTurn] is a finding rather than a gap: an unpaid promise is the defect the
 * cliffhanger rule exists to prevent.
 */
data class PromiseAnchor(
    val promiseTurn: Int = -1,
    val payoffTurn: Int? = null
)

/**
 * [reactsToPrevious] separates a joke that answers the other speaker from one delivered into the
 * void, because the rule being measured is that humor is not one speaker's job.
 */
data class HumorAnchor(
    val turn: Int = -1,
    val role: String = "",
    val reactsToPrevious: Boolean = false
)
