package com.aisummarypodcast.llm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScriptCleanupTest {

    @Test
    fun `stripOutsideSpeakerTags removes leading LLM preamble`() {
        val script = """
            I have enough context. Writing the script now.

            <interviewer>Welcome to the show.</interviewer>
            <expert>Glad to be here.</expert>
        """.trimIndent()

        val result = stripOutsideSpeakerTags(script)

        assertEquals("<interviewer>Welcome to the show.</interviewer>\n<expert>Glad to be here.</expert>", result)
    }

    @Test
    fun `stripOutsideSpeakerTags removes trailing text after last tag`() {
        val script = "<interviewer>Bye!</interviewer>\nThat concludes the script."

        val result = stripOutsideSpeakerTags(script)

        assertEquals("<interviewer>Bye!</interviewer>", result)
    }

    @Test
    fun `stripOutsideSpeakerTags keeps trailing turn with different tag than first`() {
        val script = "preamble <interviewer>Hi.</interviewer><expert>Final word.</expert>"

        val result = stripOutsideSpeakerTags(script)

        assertEquals("<interviewer>Hi.</interviewer><expert>Final word.</expert>", result)
    }

    @Test
    fun `stripOutsideSpeakerTags preserves text between turns`() {
        val script = "<host>One.</host>\nbridge\n<cohost>Two.</cohost>"

        val result = stripOutsideSpeakerTags(script)

        assertEquals(script, result)
    }

    @Test
    fun `stripOutsideSpeakerTags returns untagged script unchanged`() {
        val script = "Just a plain monologue script without tags."

        val result = stripOutsideSpeakerTags(script)

        assertEquals(script, result)
    }

    @Test
    fun `stripLeadingMetaCommentary removes writing-the-script preamble`() {
        val script = "I have plenty of context now. Let me write the script.\n\nWelcome to the show, it's Friday."

        val result = stripLeadingMetaCommentary(script)

        assertEquals("Welcome to the show, it's Friday.", result)
    }

    @Test
    fun `stripLeadingMetaCommentary removes now-writing preamble`() {
        val script = "I have what I need. Now writing the script.\n\nGood morning everyone."

        val result = stripLeadingMetaCommentary(script)

        assertEquals("Good morning everyone.", result)
    }

    @Test
    fun `stripLeadingMetaCommentary keeps genuine spoken opening`() {
        val script = "What happens when one AI commands a thousand others?\n\nWelcome to the show."

        val result = stripLeadingMetaCommentary(script)

        assertEquals(script, result)
    }

    @Test
    fun `stripLeadingMetaCommentary keeps single-paragraph script`() {
        val script = "Welcome to the show, here is today's news in one breath."

        val result = stripLeadingMetaCommentary(script)

        assertEquals(script, result)
    }

    @Test
    fun `stripLeadingMetaCommentary keeps long first paragraph even with keyword`() {
        val longOpening = "Welcome to the show. " + "Today we talk about a script kiddie attack. ".repeat(10)
        val script = "$longOpening\n\nMore content."

        val result = stripLeadingMetaCommentary(script)

        assertEquals(script, result)
    }

    // --- Square-bracketed speaker openers ---

    private val interviewRoles = setOf("interviewer", "expert")

    @Test
    fun `normalizeSquareBracketSpeakerTags rewrites a square-bracketed opener`() {
        val script = "[interviewer]Welcome to the show.</interviewer><expert>Glad to be here.</expert>"

        val result = normalizeSquareBracketSpeakerTags(script, interviewRoles)

        assertEquals("<interviewer>Welcome to the show.</interviewer><expert>Glad to be here.</expert>", result)
    }

    @Test
    fun `normalizeSquareBracketSpeakerTags rescues an opening turn that strip would otherwise drop`() {
        // Episode 184's exact failure: the model wrote the whole cold open correctly and closed it
        // with </interviewer>, but opened it with a square bracket, so the turn was silently deleted.
        val script = "[interviewer]Eighty five percent. This is the show, it's Friday.</interviewer>" +
            "<expert>[warm and conversational] And what a week to close out.</expert>" +
            "<interviewer>That's the show.</interviewer>"

        val stripped = stripOutsideSpeakerTags(normalizeSquareBracketSpeakerTags(script, interviewRoles))

        assertTrue(stripped.startsWith("<interviewer>Eighty five percent."), "opening turn survived: $stripped")
        assertEquals(3, SPEAKER_TURN_PATTERN.findAll(stripped).count())
    }

    @Test
    fun `stripOutsideSpeakerTags without normalization drops the square-bracketed opener`() {
        val script = "[interviewer]Eighty five percent. This is the show.</interviewer>" +
            "<expert>And what a week.</expert>"

        val stripped = stripOutsideSpeakerTags(script)

        assertEquals("<expert>And what a week.</expert>", stripped)
    }

    @Test
    fun `normalizeSquareBracketSpeakerTags leaves delivery cues alone`() {
        val script = "<expert>[warm and conversational] Hello there.</expert>"

        val result = normalizeSquareBracketSpeakerTags(script, interviewRoles)

        assertEquals(script, result)
    }

    @Test
    fun `normalizeSquareBracketSpeakerTags ignores a role not configured for this podcast`() {
        val script = "[narrator]Once upon a time.</narrator><expert>Hi.</expert>"

        val result = normalizeSquareBracketSpeakerTags(script, interviewRoles)

        assertEquals(script, result)
    }

    @Test
    fun `normalizeSquareBracketSpeakerTags leaves an opener with no closing tag alone`() {
        val script = "[expert] a stray mention with no close<interviewer>Hi.</interviewer>"

        val result = normalizeSquareBracketSpeakerTags(script, interviewRoles)

        assertEquals(script, result)
    }

    @Test
    fun `normalizeSquareBracketSpeakerTags does not swallow a later well-formed turn`() {
        // The closing </expert> here belongs to the well-formed turn, not to the square-bracketed
        // text, so rewriting the opener would nest one turn inside another.
        val script = "[expert] unterminated <interviewer>Question?</interviewer><expert>Answer.</expert>"

        val result = normalizeSquareBracketSpeakerTags(script, interviewRoles)

        assertEquals(script, result)
    }

    @Test
    fun `normalizeSquareBracketSpeakerTags rewrites every mis-typed opener`() {
        val script = "[interviewer]First.</interviewer><expert>Second.</expert>[interviewer]Third.</interviewer>"

        val result = normalizeSquareBracketSpeakerTags(script, interviewRoles)

        assertEquals(3, SPEAKER_TURN_PATTERN.findAll(result).count())
    }

    @Test
    fun `normalizeSquareBracketSpeakerTags returns the script unchanged when no roles are configured`() {
        val script = "[interviewer]Welcome.</interviewer>"

        assertEquals(script, normalizeSquareBracketSpeakerTags(script, emptySet()))
    }
}
