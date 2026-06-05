package com.aisummarypodcast.llm

import org.junit.jupiter.api.Assertions.assertEquals
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
}
