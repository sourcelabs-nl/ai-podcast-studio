package com.aisummarypodcast.eval

/** Turns and words a single speaker holds, and their share of the episode. */
data class RoleMetrics(
    val role: String,
    val turns: Int,
    val words: Int,
    val wordShare: Double
)

/**
 * How long one speaker's turns run. [turnsOverSentenceCap] counts turns longer than the compose
 * prompt allows, so it reads directly against the rule rather than against an invented threshold.
 *
 * [medianWords] is rounded down on an even number of turns.
 */
data class TurnLengthMetrics(
    val medianWords: Int,
    val maxWords: Int,
    val turnsOverSentenceCap: Int
)

/** A turn shaped like a token of listening. See `ScriptMetrics.backchannelCandidates`. */
data class BackchannelCandidate(
    val turnIndex: Int,
    val role: String,
    val words: Int,
    val text: String
)

/** Consecutive turns held by one speaker, reported with the indices involved. */
data class SameSpeakerRun(
    val role: String,
    val turnIndexes: List<Int>
)

data class ScriptMetricsResult(
    val turnCount: Int,
    val totalWords: Int,
    val roles: List<RoleMetrics>,
    val turnLengthByRole: Map<String, TurnLengthMetrics>,
    /**
     * Laugh tags per role, a proxy for humor distribution and never a humor count: a joke carrying
     * no laugh tag is invisible here.
     */
    val laughTagsByRole: Map<String, Int>,
    val backchannelCandidates: List<BackchannelCandidate>,
    val sameSpeakerRuns: List<SameSpeakerRun>
) {
    companion object {
        fun empty() = ScriptMetricsResult(0, 0, emptyList(), emptyMap(), emptyMap(), emptyList(), emptyList())
    }
}

/** One episode's metrics, for reading the archive as a distribution. */
data class EpisodeScriptMetrics(
    val episodeId: Long,
    val generatedAt: String?,
    val durationSeconds: Int?,
    val metrics: ScriptMetricsResult
)
