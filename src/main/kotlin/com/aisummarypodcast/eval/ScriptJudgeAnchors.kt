package com.aisummarypodcast.eval

/**
 * What the judge returns: positions in the script, never measurements.
 *
 * Every field can be checked by opening the script at the named turn and looking, which is what
 * makes the judge auditable. A count or a rating asked of the model could not be checked and would
 * drift between judge model versions with nothing to notice the drift, so distances, counts and
 * ratios are computed from these indices in [AttentionScoring] instead.
 */
data class ScriptJudgeAnchors(
    val promises: List<PromiseAnchor> = emptyList(),
    val humorBeats: List<HumorAnchor> = emptyList(),
    val teaserTopics: List<String> = emptyList()
)

/**
 * A forward-looking promise and the turn that pays it off.
 *
 * [payoffTurn] is null when the promise is never paid off, which is a finding rather than a gap:
 * an unpaid promise is exactly the defect the cliffhanger rule exists to prevent.
 */
data class PromiseAnchor(
    val promiseTurn: Int = -1,
    val payoffTurn: Int? = null
)

/**
 * A humor beat, by position and speaker.
 *
 * [reactsToPrevious] separates a joke that answers the other speaker from one delivered into the
 * void, because the rule being measured is that humor is not one speaker's job.
 */
data class HumorAnchor(
    val turn: Int = -1,
    val role: String = "",
    val reactsToPrevious: Boolean = false
)
