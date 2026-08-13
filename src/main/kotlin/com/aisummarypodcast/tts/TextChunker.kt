package com.aisummarypodcast.tts

object TextChunker {

    private const val DEFAULT_MAX_CHUNK_SIZE = 4096

    /**
     * Boundaries to split on, most natural first. Matching Inworld's reference chunker, a split is
     * only pushed to the next boundary when a piece still exceeds the maximum, so a splice lands at
     * a paragraph break where one is available and only degrades to a mid-sentence cut as a last resort.
     */
    private val SEPARATORS = listOf(
        Regex("\\n{2,}"),         // paragraph break
        Regex("\\n"),             // line break
        Regex("(?<=[.!?])\\s+"),  // sentence boundary
        Regex(" ")                // word boundary
    )

    fun chunk(text: String, maxChunkSize: Int = DEFAULT_MAX_CHUNK_SIZE): List<String> {
        if (text.length <= maxChunkSize) return listOf(text)
        return split(text, maxChunkSize, 0)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun split(text: String, maxChunkSize: Int, separatorIndex: Int): List<String> {
        if (text.length <= maxChunkSize) return listOf(text)
        if (separatorIndex >= SEPARATORS.size) return text.chunked(maxChunkSize)

        val parts = splitKeepingSeparators(text, SEPARATORS[separatorIndex])
        if (parts.size <= 1) return split(text, maxChunkSize, separatorIndex + 1)

        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        for (part in parts) {
            if (part.length > maxChunkSize) {
                if (current.isNotEmpty()) {
                    chunks.add(current.toString())
                    current.clear()
                }
                chunks.addAll(split(part, maxChunkSize, separatorIndex + 1))
                continue
            }
            if (current.length + part.length > maxChunkSize) {
                chunks.add(current.toString())
                current.clear()
            }
            current.append(part)
        }
        if (current.isNotEmpty()) chunks.add(current.toString())
        return chunks
    }

    /** Keeps each separator attached to the text before it, so paragraph structure survives chunking. */
    private fun splitKeepingSeparators(text: String, separator: Regex): List<String> {
        val parts = mutableListOf<String>()
        var start = 0
        for (match in separator.findAll(text)) {
            val end = match.range.last + 1
            if (end > start) parts.add(text.substring(start, end))
            start = end
        }
        if (start < text.length) parts.add(text.substring(start))
        return parts
    }
}
