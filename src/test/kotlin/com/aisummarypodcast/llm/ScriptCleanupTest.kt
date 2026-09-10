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

    // --- closeUnterminatedFinalTurn ---------------------------------------------------------------

    private val roles = setOf("interviewer", "expert")

    @Test
    fun `closeUnterminatedFinalTurn closes an unclosed last turn`() {
        val script = "<interviewer>Welcome.</interviewer>\n<expert>So, closing the loop on that question."

        val result = closeUnterminatedFinalTurn(script, roles)

        assertEquals(
            "<interviewer>Welcome.</interviewer>\n<expert>So, closing the loop on that question.</expert>",
            result
        )
    }

    @Test
    fun `closeUnterminatedFinalTurn rescues a closing that strip would otherwise drop`() {
        // Episode 202: the model wrote its closing paragraph and only forgot the closing tag, and
        // the whole turn was discarded as untagged trailing text.
        val script = "<interviewer>Welcome.</interviewer>\n<expert>And that is where we leave it today."

        val stripped = stripOutsideSpeakerTags(closeUnterminatedFinalTurn(script, roles))

        assertTrue(stripped.contains("And that is where we leave it today."))
    }

    @Test
    fun `stripOutsideSpeakerTags without the recovery drops the unclosed closing`() {
        val script = "<interviewer>Welcome.</interviewer>\n<expert>And that is where we leave it today."

        val stripped = stripOutsideSpeakerTags(script)

        assertEquals("<interviewer>Welcome.</interviewer>", stripped)
    }

    @Test
    fun `closeUnterminatedFinalTurn keeps delivery markup inside the recovered turn`() {
        val script = "<interviewer>Welcome.</interviewer>\n<expert>One last thing.<break time=\"1s\" /> Thanks for listening."

        val result = closeUnterminatedFinalTurn(script, roles)

        assertTrue(result.endsWith("Thanks for listening.</expert>"))
        assertTrue(result.contains("<break time=\"1s\" />"))
    }

    @Test
    fun `closeUnterminatedFinalTurn leaves a well-formed script alone`() {
        val script = "<interviewer>Welcome.</interviewer>\n<expert>Glad to be here.</expert>"

        assertEquals(script, closeUnterminatedFinalTurn(script, roles))
    }

    @Test
    fun `closeUnterminatedFinalTurn leaves plain trailing commentary alone`() {
        val script = "<interviewer>Welcome.</interviewer>\nThat is the script, let me know if you want changes."

        assertEquals(script, closeUnterminatedFinalTurn(script, roles))
    }

    @Test
    fun `closeUnterminatedFinalTurn does not glue several malformed turns into one`() {
        val script = "<interviewer>Welcome.</interviewer>\n<expert>First point.<interviewer>Second point."

        assertEquals(script, closeUnterminatedFinalTurn(script, roles))
    }

    @Test
    fun `closeUnterminatedFinalTurn ignores a role this podcast does not use`() {
        val script = "<interviewer>Welcome.</interviewer>\n<narrator>Unclosed and unknown."

        assertEquals(script, closeUnterminatedFinalTurn(script, roles))
    }

    @Test
    fun `closeUnterminatedFinalTurn recovers a script that is one unclosed turn`() {
        val script = "<expert>The only thing anyone said today."

        assertEquals("\n<expert>The only thing anyone said today.</expert>", closeUnterminatedFinalTurn(script, roles))
    }

    @Test
    fun `closeUnterminatedFinalTurn leaves an empty trailing turn alone`() {
        val script = "<interviewer>Welcome.</interviewer>\n<expert>   "

        assertEquals(script, closeUnterminatedFinalTurn(script, roles))
    }
}
