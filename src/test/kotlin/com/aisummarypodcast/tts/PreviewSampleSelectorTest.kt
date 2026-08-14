package com.aisummarypodcast.tts

import com.aisummarypodcast.store.PodcastStyle
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PreviewSampleSelectorTest {

    private val maxChunkSize = 200

    private fun turn(role: String, text: String) = "<$role>$text</$role>"

    @Test
    fun `monologue sample is the opening chunk only`() {
        val script = (1..20).joinToString("\n\n") { "Paragraph $it with a reasonable amount of prose in it." }

        val sample = PreviewSampleSelector.select(script, PodcastStyle.NEWS_BRIEFING, maxChunkSize)

        assertTrue(sample.text.isNotBlank())
        assertTrue(sample.text.length <= maxChunkSize)
        assertTrue(script.startsWith(sample.text.take(20)))
        assertTrue(sample.roles.isEmpty())
    }

    @Test
    fun `monologue sample ends on a sentence boundary`() {
        val script = (1..30).joinToString(" ") { "Sentence number $it lands here." }

        val sample = PreviewSampleSelector.select(script, PodcastStyle.NEWS_BRIEFING, maxChunkSize)

        assertTrue(sample.text.endsWith("."))
    }

    @Test
    fun `short monologue is returned whole`() {
        val script = "A single short paragraph."

        val sample = PreviewSampleSelector.select(script, PodcastStyle.CASUAL, maxChunkSize)

        assertEquals(script, sample.text)
    }

    @Test
    fun `dialogue sample keeps whole turns and never cuts one mid-sentence`() {
        val script = (1..10).joinToString("\n") { index ->
            turn(if (index % 2 == 1) "host" else "cohost", "Turn $index says something worth about forty characters here.")
        }

        val sample = PreviewSampleSelector.select(script, PodcastStyle.DIALOGUE, maxChunkSize)

        val turns = DialogueScriptParser.parse(sample.text)
        assertTrue(turns.isNotEmpty())
        for (parsed in turns) {
            assertTrue(script.contains(parsed.text), "turn was cut: '${parsed.text}'")
        }
    }

    @Test
    fun `dialogue sample covers two speakers`() {
        val script = (1..10).joinToString("\n") { index ->
            turn(if (index % 2 == 1) "host" else "cohost", "Turn $index says something worth about forty characters here.")
        }

        val sample = PreviewSampleSelector.select(script, PodcastStyle.DIALOGUE, maxChunkSize)

        assertEquals(listOf("host", "cohost"), sample.roles)
    }

    @Test
    fun `dialogue extends past one chunk when the opening turn monopolises it`() {
        val longOpening = "The host talks at length. ".repeat(40)
        val script = turn("host", longOpening) + "\n" + turn("cohost", "Finally a reply.")

        val sample = PreviewSampleSelector.select(script, PodcastStyle.DIALOGUE, maxChunkSize)

        assertEquals(listOf("host", "cohost"), sample.roles)
        assertTrue(sample.text.contains("Finally a reply."))
        assertTrue(sample.text.length > maxChunkSize)
    }

    @Test
    fun `dialogue extension stays contiguous through repeated turns by the same speaker`() {
        val script = listOf(
            turn("host", "Opening remark that runs on for a while to eat into the chunk budget here."),
            turn("host", "Second remark from the same speaker, also fairly long so the budget runs out."),
            turn("host", "Third remark, still the host, filling the rest of the available budget now."),
            turn("cohost", "The co-host finally speaks.")
        ).joinToString("\n")

        val sample = PreviewSampleSelector.select(script, PodcastStyle.DIALOGUE, maxChunkSize)

        val turns = DialogueScriptParser.parse(sample.text)
        assertEquals(listOf("host", "host", "host", "cohost"), turns.map { it.role })
    }

    @Test
    fun `interview sample auditions both the interviewer and the expert`() {
        val script = (1..8).joinToString("\n") { index ->
            turn(if (index % 2 == 1) "interviewer" else "expert", "Interview turn $index with a sentence of real length in it.")
        }

        val sample = PreviewSampleSelector.select(script, PodcastStyle.INTERVIEW, maxChunkSize)

        assertEquals(listOf("interviewer", "expert"), sample.roles)
    }

    @Test
    fun `dialogue with only one speaker returns what there is`() {
        val script = turn("host", "The only voice in this script.")

        val sample = PreviewSampleSelector.select(script, PodcastStyle.DIALOGUE, maxChunkSize)

        assertEquals(listOf("host"), sample.roles)
        assertTrue(sample.text.contains("The only voice in this script."))
    }

    @Test
    fun `untagged dialogue script falls back to the opening chunk`() {
        val script = (1..20).joinToString("\n\n") { "Untagged paragraph $it with some prose." }

        val sample = PreviewSampleSelector.select(script, PodcastStyle.DIALOGUE, maxChunkSize)

        assertTrue(sample.text.isNotBlank())
        assertTrue(sample.roles.isEmpty())
        assertTrue(sample.text.length <= maxChunkSize)
    }

    @Test
    fun `blank script yields an empty sample`() {
        val sample = PreviewSampleSelector.select("   ", PodcastStyle.CASUAL, maxChunkSize)

        assertEquals("", sample.text)
        assertTrue(sample.roles.isEmpty())
    }
}
