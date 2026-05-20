package com.aisummarypodcast.tts

object TtsScriptSanitizer {

    private val DASHES = Regex("[—–]")
    private val COMMA_BEFORE_TERMINATOR = Regex("\\s*,\\s*([.!?])")
    private val DUPLICATE_SPACES = Regex(" {2,}")

    fun sanitize(script: String): String {
        var result = DASHES.replace(script, ", ")
        result = COMMA_BEFORE_TERMINATOR.replace(result, "$1")
        result = DUPLICATE_SPACES.replace(result, " ")
        return result
    }
}
