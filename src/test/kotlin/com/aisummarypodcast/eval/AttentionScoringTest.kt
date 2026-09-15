package com.aisummarypodcast.eval

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the arithmetic against fixed anchors. No model is involved in any of these: the point of
 * asking the judge for positions is that everything below is checkable without one.
 */
class AttentionScoringTest {

    @Test
    fun `a promise paid off in the next breath is not a deferral`() {
        val anchors = ScriptJudgeAnchors(promises = listOf(PromiseAnchor(4, 5)))

        val score = AttentionScoring.of(anchors).cliffhangers

        assertEquals(1, score.promises)
        assertEquals(0, score.deferredPromises)
        assertEquals(0.0, score.score)
        assertNull(score.medianDeferralTurns)
    }

    @Test
    fun `a promise paid off far later counts and its distance is derived`() {
        val anchors = ScriptJudgeAnchors(promises = listOf(PromiseAnchor(2, 24), PromiseAnchor(9, 46)))

        val score = AttentionScoring.of(anchors).cliffhangers

        assertEquals(2, score.deferredPromises)
        assertEquals(1.0, score.score)
        // Distances of 22 and 37, even count, so the median rounds down to 29.
        assertEquals(29, score.medianDeferralTurns)
    }

    @Test
    fun `an unpaid promise is reported rather than dropped`() {
        val anchors = ScriptJudgeAnchors(promises = listOf(PromiseAnchor(3, null)))

        val score = AttentionScoring.of(anchors).cliffhangers

        assertEquals(1, score.promises)
        assertEquals(1, score.unpaidPromises)
        assertEquals(0, score.deferredPromises)
    }

    @Test
    fun `a payoff before its promise is not a distance`() {
        val anchors = ScriptJudgeAnchors(promises = listOf(PromiseAnchor(20, 3)))

        val score = AttentionScoring.of(anchors).cliffhangers

        assertEquals(0, score.deferredPromises)
        assertNull(score.medianDeferralTurns)
    }

    @Test
    fun `humor from one speaker only scores half at best`() {
        val beats = (1..AttentionScoring.TARGET_HUMOR_BEATS).map { HumorAnchor(it, "interviewer", true) }

        val score = AttentionScoring.of(ScriptJudgeAnchors(humorBeats = beats)).humor

        assertEquals(0.0, score.speakerBalance)
        // Full volume, no spread: the volume half is earned and the balance half is not.
        assertEquals(0.5, score.score)
    }

    @Test
    fun `an even split scores the balance half in full`() {
        val beats = listOf(
            HumorAnchor(1, "interviewer", true),
            HumorAnchor(2, "expert", false),
            HumorAnchor(3, "interviewer", true),
            HumorAnchor(4, "expert", true)
        )

        val score = AttentionScoring.of(ScriptJudgeAnchors(humorBeats = beats)).humor

        assertEquals(0.5, score.speakerBalance)
        assertEquals(0.75, score.reactionRatio)
        // Four of six beats, and a perfect split.
        assertEquals((4.0 / 6 + 1.0) / 2, score.score)
    }

    @Test
    fun `no humor scores zero without dividing by zero`() {
        val score = AttentionScoring.of(ScriptJudgeAnchors()).humor

        assertEquals(0, score.beats)
        assertEquals(0.0, score.speakerBalance)
        assertEquals(0.0, score.reactionRatio)
        assertEquals(0.0, score.score)
    }

    @Test
    fun `teaser topics are counted distinctly and case-insensitively`() {
        val anchors = ScriptJudgeAnchors(teaserTopics = listOf("Agents", " agents ", "chips", ""))

        val score = AttentionScoring.of(anchors).teaser

        assertEquals(2, score.distinctTopics)
        assertEquals(2.0 / 3, score.score)
    }

    @Test
    fun `a component never scores above one however much it overshoots`() {
        val beats = (1..40).map { HumorAnchor(it, if (it % 2 == 0) "expert" else "interviewer", true) }
        val anchors = ScriptJudgeAnchors(
            promises = (1..10).map { PromiseAnchor(it, it + 20) },
            humorBeats = beats,
            teaserTopics = (1..10).map { "topic $it" }
        )

        val score = AttentionScoring.of(anchors)

        assertEquals(1.0, score.cliffhangers.score)
        assertEquals(1.0, score.humor.score)
        assertEquals(1.0, score.teaser.score)
        assertEquals(1.0, score.overall)
    }

    @Test
    fun `the overall score is the mean of the three components`() {
        val anchors = ScriptJudgeAnchors(
            promises = listOf(PromiseAnchor(1, 20)),
            teaserTopics = listOf("one", "two", "three")
        )

        val score = AttentionScoring.of(anchors)

        assertEquals(0.5, score.cliffhangers.score)
        assertEquals(0.0, score.humor.score)
        assertEquals(1.0, score.teaser.score)
        assertEquals(0.5, score.overall)
    }
}
