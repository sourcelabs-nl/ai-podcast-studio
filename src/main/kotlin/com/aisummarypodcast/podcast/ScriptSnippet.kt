package com.aisummarypodcast.podcast

/**
 * Pulls the text around a search term out of an episode's script, so a list row can show the
 * sentence a keyword was actually spoken in rather than only asserting that it appears somewhere.
 */
object ScriptSnippet {

    /** Characters of context kept on each side of the term. */
    private const val RADIUS = 90

    /** Dialogue scripts are tagged `<expert>` / `<interviewer>`; those are not spoken words. */
    private val SPEAKER_TAG = Regex("</?\\w+>")

    private val WHITESPACE = Regex("\\s+")

    /**
     * The earliest occurrence of any term in [texts], with surrounding context and ellipses, or
     * null when no term appears in any of them. Sources are tried in order, so the script wins over
     * a recap that repeats it.
     */
    fun firstMatch(texts: List<String?>, terms: List<String>): String? =
        texts.asSequence().mapNotNull { around(it, terms) }.firstOrNull()

    private fun around(text: String?, terms: List<String>): String? {
        if (text.isNullOrBlank() || terms.isEmpty()) return null
        val clean = WHITESPACE.replace(SPEAKER_TAG.replace(text, " "), " ").trim()
        val lower = clean.lowercase()

        val hit = terms
            .mapNotNull { term -> lower.indexOf(term.lowercase()).takeIf { it >= 0 }?.let { it to term.length } }
            .minByOrNull { it.first }
            ?: return null

        val (index, termLength) = hit
        val start = wordStart(clean, (index - RADIUS).coerceAtLeast(0))
        val end = wordEnd(clean, (index + termLength + RADIUS).coerceAtMost(clean.length))
        val prefix = if (start > 0) "..." else ""
        val suffix = if (end < clean.length) "..." else ""
        return prefix + clean.substring(start, end).trim() + suffix
    }

    /** Moves [from] forward to the next word boundary, so the snippet does not open mid-word. */
    private fun wordStart(text: String, from: Int): Int {
        if (from == 0) return 0
        val space = text.indexOf(' ', from)
        return if (space in from until text.length) space + 1 else from
    }

    /** Moves [to] back to the previous word boundary, so the snippet does not end mid-word. */
    private fun wordEnd(text: String, to: Int): Int {
        if (to >= text.length) return text.length
        val space = text.lastIndexOf(' ', to)
        return if (space > 0) space else to
    }
}
