package com.aisummarypodcast.tts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DialogueScriptParserTest {

    @Test
    fun `parses simple two-speaker dialogue`() {
        val script = "<host>Hello!</host>\n<cohost>Hi there!</cohost>"
        val turns = DialogueScriptParser.parse(script)

        assertEquals(2, turns.size)
        assertEquals(DialogueTurn("host", "Hello!"), turns[0])
        assertEquals(DialogueTurn("cohost", "Hi there!"), turns[1])
    }

    @Test
    fun `handles multi-line content within tags`() {
        val script = "<host>First line.\nSecond line.</host>"
        val turns = DialogueScriptParser.parse(script)

        assertEquals(1, turns.size)
        assertEquals("First line.\nSecond line.", turns[0].text)
    }

    @Test
    fun `handles multiple consecutive turns`() {
        val script = """
            <host>Welcome to the show!</host>
            <cohost>Great to be here.</cohost>
            <host>Let's talk about AI.</host>
            <cohost>Sure, lots happening.</cohost>
        """.trimIndent()
        val turns = DialogueScriptParser.parse(script)

        assertEquals(4, turns.size)
        assertEquals("host", turns[0].role)
        assertEquals("cohost", turns[1].role)
        assertEquals("host", turns[2].role)
        assertEquals("cohost", turns[3].role)
    }

    @Test
    fun `preserves emotion cues in brackets`() {
        val script = "<host>[cheerfully] Welcome back!</host>"
        val turns = DialogueScriptParser.parse(script)

        assertEquals(1, turns.size)
        assertEquals("[cheerfully] Welcome back!", turns[0].text)
    }

    @Test
    fun `returns empty list for empty input`() {
        assertTrue(DialogueScriptParser.parse("").isEmpty())
        assertTrue(DialogueScriptParser.parse("   ").isEmpty())
    }

    @Test
    fun `ignores text outside tags with warning`() {
        val script = "Some intro <host>Hello!</host> trailing text"
        val turns = DialogueScriptParser.parse(script)

        assertEquals(1, turns.size)
        assertEquals(DialogueTurn("host", "Hello!"), turns[0])
    }

    @Test
    fun `handles tags without whitespace between them`() {
        val script = "<host>Hello!</host><cohost>Hi!</cohost>"
        val turns = DialogueScriptParser.parse(script)

        assertEquals(2, turns.size)
    }

    @Test
    fun `recovers turn when closing tag does not match opening tag`() {
        // LLM emits <expert>...</interviewer> — the role comes from the opening tag.
        val script = "<interviewer>What happened?</interviewer><expert>A lot happened.</interviewer>"
        val turns = DialogueScriptParser.parse(script)

        assertEquals(2, turns.size)
        assertEquals(DialogueTurn("interviewer", "What happened?"), turns[0])
        assertEquals(DialogueTurn("expert", "A lot happened."), turns[1])
    }

    @Test
    fun `recovers turn when closing tag is missing before next opening tag`() {
        val script = "<interviewer>First question<expert>The answer.</expert>"
        val turns = DialogueScriptParser.parse(script)

        assertEquals(2, turns.size)
        assertEquals(DialogueTurn("interviewer", "First question"), turns[0])
        assertEquals(DialogueTurn("expert", "The answer."), turns[1])
    }

    @Test
    fun `keeps consecutive turns of the same speaker`() {
        val script = "<interviewer>One.</interviewer><interviewer>Two.</interviewer>"
        val turns = DialogueScriptParser.parse(script)

        assertEquals(2, turns.size)
        assertEquals(DialogueTurn("interviewer", "One."), turns[0])
        assertEquals(DialogueTurn("interviewer", "Two."), turns[1])
    }

    @Test
    fun `recovers final turn when closing tag is missing entirely`() {
        val script = "<interviewer>Question?</interviewer><expert>Unterminated answer."
        val turns = DialogueScriptParser.parse(script)

        assertEquals(2, turns.size)
        assertEquals(DialogueTurn("expert", "Unterminated answer."), turns[1])
    }
}
