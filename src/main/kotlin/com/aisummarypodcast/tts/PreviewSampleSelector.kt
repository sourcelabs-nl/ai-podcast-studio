package com.aisummarypodcast.tts

import com.aisummarypodcast.store.PodcastStyle

/** The opening slice of a script chosen for a short audition, and the speaker roles it covers. */
data class PreviewSample(val text: String, val roles: List<String>)

/**
 * Picks the opening slice of a script to audition through the real TTS path. The slice is roughly
 * one provider chunk, so it costs about a cent and returns in seconds, and it always ends on a
 * natural boundary rather than mid-sentence.
 */
object PreviewSampleSelector {

    /** A dialogue sample is worthless unless it lets the listener hear both configured voices. */
    private const val MIN_DIALOGUE_SPEAKERS = 2

    private val MULTI_VOICE_STYLES = setOf(PodcastStyle.DIALOGUE, PodcastStyle.INTERVIEW)

    fun select(script: String, style: PodcastStyle, maxChunkSize: Int): PreviewSample {
        if (script.isBlank()) return PreviewSample("", emptyList())
        if (style !in MULTI_VOICE_STYLES) return PreviewSample(firstChunk(script, maxChunkSize), emptyList())

        val turns = DialogueScriptParser.parse(script)
        if (turns.isEmpty()) return PreviewSample(firstChunk(script, maxChunkSize), emptyList())

        val selected = selectTurns(turns, maxChunkSize)
        return PreviewSample(
            text = selected.joinToString("\n\n") { "<${it.role}>${it.text}</${it.role}>" },
            roles = selected.map { it.role }.distinct()
        )
    }

    /**
     * A monologue has no speaker turns, so the chunker's own boundary is the natural cut: it splits
     * at a paragraph break where one is available and only degrades to a mid-sentence cut for a
     * single unbreakable run longer than a whole chunk.
     */
    private fun firstChunk(script: String, maxChunkSize: Int): String =
        TextChunker.chunk(script, maxChunkSize).firstOrNull()?.trim() ?: script.trim()

    /**
     * Whole turns from the start of the script: as many as fit in one chunk (always at least the
     * opening turn, however long it is), then however many more are needed to reach a second
     * speaker. Turns are never cut, so the sample ends where a speaker does, and the extension stays
     * contiguous so the audition plays as an unbroken stretch of the conversation.
     */
    private fun selectTurns(turns: List<DialogueTurn>, maxChunkSize: Int): List<DialogueTurn> {
        val selected = mutableListOf<DialogueTurn>()
        var characters = 0
        var index = 0
        while (index < turns.size) {
            val turn = turns[index]
            if (selected.isNotEmpty() && characters + turn.text.length > maxChunkSize) break
            selected.add(turn)
            characters += turn.text.length
            index++
        }

        if (distinctRoles(selected) >= MIN_DIALOGUE_SPEAKERS) return selected

        val heardRoles = selected.map { it.role }.toSet()
        val offsetToNextSpeaker = turns.subList(index, turns.size).indexOfFirst { it.role !in heardRoles }
        if (offsetToNextSpeaker < 0) return selected
        selected.addAll(turns.subList(index, index + offsetToNextSpeaker + 1))
        return selected
    }

    private fun distinctRoles(turns: List<DialogueTurn>): Int = turns.map { it.role }.distinct().size
}
