package com.aisummarypodcast.tts

object TtsScriptSanitizer {

    private val DASHES = Regex("[—–]")
    private val COMMA_BEFORE_TERMINATOR = Regex("\\s*,\\s*([.!?])")
    private val DUPLICATE_SPACES = Regex(" {2,}")

    /**
     * A slash-delimited IPA span: no whitespace or slash inside, and at least one non-ASCII
     * character. The non-ASCII requirement is what keeps ordinary prose intact — `and/or`,
     * `TCP/IP` and `input/output` are all-ASCII and are left alone.
     */
    private val PHONEME_SPAN = Regex("/(?=[^/\\s]*[^\\u0000-\\u007F])[^/\\s]{1,40}/")

    /**
     * True when [text] contains an IPA phoneme span. The provider uses this to pick a delivery mode:
     * a phoneme is a literal instruction to the engine and needs the most deterministic read
     * available, not the widest one.
     */
    fun containsPhoneme(text: String): Boolean = PHONEME_SPAN.containsMatchIn(text)

    /**
     * Prepares a script for a TTS provider.
     *
     * [pronunciations] is the podcast's pronunciation dictionary (term to IPA). Any IPA span in the
     * script that is not one of its values is removed: the compose prompt says IPA notation is
     * reserved for the listed terms, but the model does not always obey. A script whose dictionary
     * held only `Jarno` came back with an invented `/stɛfan/` for the other speaker, and the engine
     * reads an unintended transcription out as a mispronounced name. The intended word cannot be
     * recovered from a phoneme string, so the span is dropped rather than guessed at.
     */
    fun sanitize(script: String, pronunciations: Map<String, String> = emptyMap()): String {
        val allowed = pronunciations.values.map { it.trim() }.toSet()
        var result = PHONEME_SPAN.replace(script) { match ->
            if (match.value in allowed) match.value else ""
        }
        result = DASHES.replace(result, ", ")
        result = COMMA_BEFORE_TERMINATOR.replace(result, "$1")
        result = DUPLICATE_SPACES.replace(result, " ")
        return result
    }
}
