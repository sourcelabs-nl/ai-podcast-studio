package com.aisummarypodcast.tts

import org.slf4j.LoggerFactory

data class DialogueTurn(val role: String, val text: String)

object DialogueScriptParser {

    private val log = LoggerFactory.getLogger(javaClass)

    // Matches any opening (<role>) or closing (</role>) speaker tag.
    private val TAG_TOKEN = Regex("</?(\\w+)>")

    /**
     * Parses a tagged dialogue script into speaker turns.
     *
     * Tolerant of malformed tags the LLM occasionally emits (mismatched closing tags such as
     * `<expert>...</interviewer>`, or a missing closing tag before the next opening tag): a turn's
     * role is taken from its opening tag, and the turn ends at the next tag token regardless of what
     * that token says. This prevents silently dropping spoken turns when the closing tag is wrong.
     *
     * The position of a turn in the returned list is its public identity. `ScriptJudge` numbers
     * turns for the judge with `parse(script).mapIndexed`, `ScriptMetrics` numbers
     * `BackchannelCandidate.turnIndex` the same way, and the dashboard's evaluation tab jumps to a
     * turn by that number using its own mirrored parser in `frontend/src/components/script-viewer.tsx`.
     * A change here that shifts indices sends a reviewer to the wrong turn, silently. Change one
     * parser only with the other in hand.
     */
    fun parse(script: String): List<DialogueTurn> {
        if (script.isBlank()) return emptyList()

        val turns = mutableListOf<DialogueTurn>()
        var openRole: String? = null
        var lastEnd = 0

        for (match in TAG_TOKEN.findAll(script)) {
            val preceding = script.substring(lastEnd, match.range.first).trim()
            val role = openRole
            if (role != null) {
                // The text since the opening tag is this turn's content; any tag token ends it.
                if (preceding.isNotEmpty()) {
                    turns.add(DialogueTurn(role, preceding))
                }
            } else if (preceding.isNotEmpty()) {
                log.warn("Text found outside speaker tags, ignoring: '{}'", preceding.take(100))
            }

            val isOpening = script[match.range.first + 1] != '/'
            openRole = if (isOpening) match.groupValues[1] else null
            lastEnd = match.range.last + 1
        }

        val trailing = script.substring(lastEnd).trim()
        if (trailing.isNotEmpty()) {
            val role = openRole
            if (role != null) {
                turns.add(DialogueTurn(role, trailing))
            } else {
                log.warn("Text found outside speaker tags, ignoring: '{}'", trailing.take(100))
            }
        }

        return turns
    }
}
