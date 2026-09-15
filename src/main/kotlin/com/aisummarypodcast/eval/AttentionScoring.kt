package com.aisummarypodcast.eval

/**
 * Turns judged anchors into numbers.
 *
 * The targets below are the shape the prompt rules ask for, not thresholds anything is held to.
 * They came from auditing episodes 208 and 209 by hand, which is a sample of two, so they set the
 * point at which a component stops earning more credit and nothing else. Whether an episode is good
 * is decided against the distribution over the archive, once there is one.
 */
object AttentionScoring {

    /** A payoff nearer than this is not a deferral; it is the same thought finishing itself. */
    const val MIN_DEFERRAL_TURNS = 5

    /** Episode 209, audited as good, had 2 forward hooks. */
    const val TARGET_DEFERRED_PROMISES = 2

    /** Episode 209 had roughly 10 humor beats against episode 208's roughly 3. */
    const val TARGET_HUMOR_BEATS = 6

    /** Episode 209's teaser named 3 topics; episode 208's named 1. */
    const val TARGET_TEASER_TOPICS = 3

    fun of(anchors: ScriptJudgeAnchors): AttentionScore {
        val cliffhangers = cliffhangerScore(anchors.promises)
        val humor = humorScore(anchors.humorBeats)
        val teaser = teaserScore(anchors.teaserTopics)
        return AttentionScore(
            cliffhangers = cliffhangers,
            humor = humor,
            teaser = teaser,
            overall = (cliffhangers.score + humor.score + teaser.score) / 3
        )
    }

    private fun cliffhangerScore(promises: List<PromiseAnchor>): CliffhangerScore {
        val distances = promises.mapNotNull { promise ->
            promise.payoffTurn?.let { it - promise.promiseTurn }
        }.filter { it > 0 }
        val deferred = distances.filter { it >= MIN_DEFERRAL_TURNS }
        return CliffhangerScore(
            promises = promises.size,
            deferredPromises = deferred.size,
            unpaidPromises = promises.count { it.payoffTurn == null },
            medianDeferralTurns = median(deferred),
            score = share(deferred.size, TARGET_DEFERRED_PROMISES)
        )
    }

    private fun humorScore(beats: List<HumorAnchor>): HumorScore {
        val perRole = beats.groupingBy { it.role }.eachCount().values
        // Two speakers splitting beats evenly gives 0.5, so doubling puts an even split at 1.0.
        // A single role in the grouping means the other speaker made no jokes at all: it is absent
        // from the grouping rather than present with a count of zero, so it is handled explicitly.
        val balance = if (perRole.size < 2) 0.0 else perRole.min().toDouble() / beats.size
        val reactionRatio = if (beats.isEmpty()) 0.0 else beats.count { it.reactsToPrevious }.toDouble() / beats.size
        // Volume and spread are both required: ten jokes from one speaker is the defect the rule
        // names, and so is a perfect split of two jokes.
        return HumorScore(
            beats = beats.size,
            speakerBalance = balance,
            reactionRatio = reactionRatio,
            score = (share(beats.size, TARGET_HUMOR_BEATS) + minOf(balance * 2, 1.0)) / 2
        )
    }

    private fun teaserScore(topics: List<String>): TeaserScore {
        val distinct = topics.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
        return TeaserScore(distinct.size, share(distinct.size, TARGET_TEASER_TOPICS))
    }

    private fun share(actual: Int, target: Int): Double = minOf(actual.toDouble() / target, 1.0)

    /** The median, rounded down on an even count, so 1 and 2 give 1. Null on an empty list. */
    private fun median(values: List<Int>): Int? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    }
}
