package com.aisummarypodcast.eval

import com.aisummarypodcast.tts.DialogueScriptParser

/**
 * Structural facts about an episode script, computed by reading it and nothing else.
 *
 * Deliberately descriptive: no thresholds, no pass or fail, no composite number. What a good
 * episode looks like is not yet known, and encoding today's guess about it here would be the same
 * unverified opinion these metrics exist to replace. Judging what needs reading comprehension,
 * whether a promise is genuinely deferred or a line is funny, belongs to the judged layer.
 */
object ScriptMetrics {

    /**
     * Sentences per turn beyond which a turn exceeds what the compose prompt allows. Mirrors the
     * prompt's "3-4 sentences" cap so the count means something against the rule as written.
     */
    const val SENTENCE_CAP = 4

    /** Words up to which a turn is short enough to be a token of listening rather than a contribution. */
    const val BACKCHANNEL_MAX_WORDS = 8

    /** Bracketed delivery cues and sound tags, which are not spoken words. */
    private val BRACKETED_CUE = Regex("\\[[^\\[\\]]*]")
    private val LAUGH_TAG = Regex("\\[laugh[^\\[\\]]*]", RegexOption.IGNORE_CASE)
    private val SENTENCE_END = Regex("[.!?]+")

    fun of(script: String): ScriptMetricsResult {
        val turns = DialogueScriptParser.parse(script).map { turn ->
            val spoken = BRACKETED_CUE.replace(turn.text, " ")
            MeasuredTurn(
                role = turn.role,
                words = spoken.split(Regex("\\s+")).count { it.isNotBlank() },
                sentences = SENTENCE_END.split(spoken).count { it.isNotBlank() },
                laughTags = LAUGH_TAG.findAll(turn.text).count(),
                text = turn.text
            )
        }
        if (turns.isEmpty()) return ScriptMetricsResult.empty()

        val totalWords = turns.sumOf { it.words }
        return ScriptMetricsResult(
            turnCount = turns.size,
            totalWords = totalWords,
            roles = turns.groupBy { it.role }.map { (role, roleTurns) ->
                RoleMetrics(
                    role = role,
                    turns = roleTurns.size,
                    words = roleTurns.sumOf { it.words },
                    wordShare = if (totalWords == 0) 0.0 else roleTurns.sumOf { it.words }.toDouble() / totalWords
                )
            }.sortedByDescending { it.words },
            turnLengthByRole = turns.groupBy { it.role }.mapValues { (_, roleTurns) ->
                TurnLengthMetrics(
                    medianWords = median(roleTurns.map { it.words }),
                    maxWords = roleTurns.maxOf { it.words },
                    turnsOverSentenceCap = roleTurns.count { it.sentences > SENTENCE_CAP }
                )
            },
            laughTagsByRole = turns.filter { it.laughTags > 0 }
                .groupBy { it.role }
                .mapValues { (_, roleTurns) -> roleTurns.sumOf { it.laughTags } },
            backchannelCandidates = backchannelCandidates(turns),
            sameSpeakerRuns = sameSpeakerRuns(turns)
        )
    }

    /**
     * Turns that have the shape of a backchannel: a short turn whose neighbours are both the other
     * speaker, so the speaker who was interrupted resumed.
     *
     * Candidates, not backchannels, and the gap is wider than it looks. The device is defined by
     * intent, a token of pure listening, while the shape is also the shape of ordinary alternation:
     * in a strictly alternating script every short turn has the other speaker on both sides, so
     * this reduces to "short turns and where they are". Whether the speaker actually resumed a
     * thought is in the text and not in the structure.
     *
     * So the list is something to read, not a count to trust, and the text is returned for that.
     */
    private fun backchannelCandidates(turns: List<MeasuredTurn>): List<BackchannelCandidate> =
        (1 until turns.size - 1).mapNotNull { i ->
            val turn = turns[i]
            val before = turns[i - 1]
            val after = turns[i + 1]
            if (turn.words <= BACKCHANNEL_MAX_WORDS && before.role == after.role && before.role != turn.role) {
                BackchannelCandidate(turnIndex = i, role = turn.role, words = turn.words, text = turn.text.trim())
            } else {
                null
            }
        }

    /**
     * Runs of consecutive turns by one speaker, located rather than flagged. A resumed turn after a
     * backchannel is legitimate and a continued point is not, and only the text tells them apart.
     */
    private fun sameSpeakerRuns(turns: List<MeasuredTurn>): List<SameSpeakerRun> {
        val runs = mutableListOf<SameSpeakerRun>()
        var start = 0
        for (i in 1..turns.size) {
            if (i == turns.size || turns[i].role != turns[start].role) {
                if (i - start > 1) runs.add(SameSpeakerRun(turns[start].role, (start until i).toList()))
                start = i
            }
        }
        return runs
    }

    /** The median, rounded down on an even count, so 1 and 2 words give 1. */
    private fun median(values: List<Int>): Int {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    }

    private data class MeasuredTurn(
        val role: String,
        val words: Int,
        val sentences: Int,
        val laughTags: Int,
        val text: String
    )
}
