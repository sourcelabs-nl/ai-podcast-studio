package com.aisummarypodcast.eval

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScriptMetricsTest {

    @Test
    fun `counts turns and words per role`() {
        val script = "<interviewer>One two three.</interviewer><expert>One two three four five.</expert>"

        val metrics = ScriptMetrics.of(script)

        assertEquals(2, metrics.turnCount)
        assertEquals(8, metrics.totalWords)
        assertEquals(5, metrics.roles.first { it.role == "expert" }.words)
        assertEquals(3, metrics.roles.first { it.role == "interviewer" }.words)
        assertEquals(0.625, metrics.roles.first { it.role == "expert" }.wordShare)
    }

    @Test
    fun `bracketed cues are not spoken words`() {
        val script = "<expert>[laugh] Two words.</expert>"

        val metrics = ScriptMetrics.of(script)

        assertEquals(2, metrics.totalWords)
        assertEquals(1, metrics.laughTagsByRole["expert"])
    }

    @Test
    fun `laugh ownership is reported per role`() {
        val script = "<interviewer>[laugh] Right.</interviewer><expert>Yes [laughs] indeed.</expert>" +
            "<interviewer>Sure.</interviewer>"

        val metrics = ScriptMetrics.of(script)

        assertEquals(1, metrics.laughTagsByRole["interviewer"])
        assertEquals(1, metrics.laughTagsByRole["expert"])
    }

    @Test
    fun `turn length reports median max and turns over the sentence cap`() {
        val short = "<expert>One.</expert>"
        val long = "<expert>One. Two. Three. Four. Five.</expert>"
        val script = short + "<interviewer>Go on.</interviewer>" + long +
            "<interviewer>And?</interviewer>" + "<expert>One. Two.</expert>"

        val expert = ScriptMetrics.of(script).turnLengthByRole.getValue("expert")

        assertEquals(2, expert.medianWords)
        assertEquals(5, expert.maxWords)
        assertEquals(1, expert.turnsOverSentenceCap)
    }

    @Test
    fun `the median of an even number of turns rounds down`() {
        val script = "<expert>One.</expert><interviewer>Go on.</interviewer>" +
            "<expert>One two three four.</expert>"

        assertEquals(2, ScriptMetrics.of(script).turnLengthByRole.getValue("expert").medianWords)
    }

    @Test
    fun `a short turn between two turns of the other speaker is a backchannel candidate`() {
        val script = "<expert>Explaining something.</expert>" +
            "<interviewer>Right.</interviewer>" +
            "<expert>Continuing the thought.</expert>"

        val candidates = ScriptMetrics.of(script).backchannelCandidates

        assertEquals(1, candidates.size)
        assertEquals(1, candidates.first().turnIndex)
        assertEquals("interviewer", candidates.first().role)
        assertEquals("Right.", candidates.first().text)
    }

    @Test
    fun `a short question between two expert turns is still a candidate`() {
        val script = "<expert>Explaining.</expert><interviewer>Why?</interviewer><expert>Because.</expert>"

        assertEquals(1, ScriptMetrics.of(script).backchannelCandidates.size)
    }

    @Test
    fun `a long turn between two expert turns is not a candidate`() {
        val long = "<interviewer>" + (1..20).joinToString(" ") { "word" } + "</interviewer>"
        val script = "<expert>Explaining.</expert>$long<expert>Continuing.</expert>"

        assertTrue(ScriptMetrics.of(script).backchannelCandidates.isEmpty())
    }

    @Test
    fun `in an alternating script every short turn is a candidate`() {
        val script = "<interviewer>A.</interviewer><expert>B.</expert><interviewer>C.</interviewer>"

        // The shape of a backchannel is also the shape of ordinary alternation, so the list is
        // something to read rather than a count to trust. Documented on ScriptMetrics.
        assertEquals(1, ScriptMetrics.of(script).backchannelCandidates.size)
    }

    @Test
    fun `consecutive turns by one speaker are located`() {
        val script = "<interviewer>A.</interviewer><interviewer>B.</interviewer>" +
            "<expert>C.</expert><expert>D.</expert><expert>E.</expert>"

        val runs = ScriptMetrics.of(script).sameSpeakerRuns

        assertEquals(2, runs.size)
        assertEquals(listOf(0, 1), runs.first { it.role == "interviewer" }.turnIndexes)
        assertEquals(listOf(2, 3, 4), runs.first { it.role == "expert" }.turnIndexes)
    }

    @Test
    fun `an alternating script has no same speaker runs`() {
        val script = "<interviewer>A.</interviewer><expert>B.</expert><interviewer>C.</interviewer>"

        assertTrue(ScriptMetrics.of(script).sameSpeakerRuns.isEmpty())
    }

    @Test
    fun `a script with one role only reports that role alone`() {
        val metrics = ScriptMetrics.of("<interviewer>Alone here.</interviewer>")

        assertEquals(1, metrics.roles.size)
        assertEquals(null, metrics.turnLengthByRole["expert"])
    }

    @Test
    fun `a script with no speaker tags is empty rather than an error`() {
        val metrics = ScriptMetrics.of("Just a monologue with no tags at all.")

        assertEquals(0, metrics.turnCount)
        assertEquals(0, metrics.totalWords)
        assertTrue(metrics.roles.isEmpty())
    }

    @Test
    fun `a blank script is empty rather than an error`() {
        assertEquals(0, ScriptMetrics.of("").turnCount)
    }
}
