package com.aisummarypodcast.podcast

import com.aisummarypodcast.llm.stripLeadingMetaCommentary

private val SPEAKER_TAG_PATTERN = Regex("</?\\w+>")
private val WHITESPACE_PATTERN = Regex("\\s+")

/**
 * Builds a clean plain-text summary from the raw script, used as a last-resort fallback when an
 * episode has neither show notes nor a recap. Strips any leaked "I'm about to write the script"
 * preamble and removes speaker tags (e.g. `<interviewer>`/`<expert>`) while keeping the spoken
 * text, so listener-facing surfaces (RSS description, sources page) never show raw tags.
 */
fun scriptFallbackSummary(script: String): String {
    val cleaned = stripLeadingMetaCommentary(script)
        .replace(SPEAKER_TAG_PATTERN, " ")
        .replace(WHITESPACE_PATTERN, " ")
        .trim()
    return cleaned.take(500) + "..."
}

/**
 * Formats an episode audio length for display, e.g. 90 -> "1 min", 754 -> "12 min", 3725 -> "1 h 2 min".
 * Returns null when no duration is known.
 */
fun formatDuration(durationSeconds: Int?): String? {
    if (durationSeconds == null || durationSeconds <= 0) return null
    val totalMinutes = durationSeconds / 60
    if (totalMinutes < 60) return "${totalMinutes.coerceAtLeast(1)} min"
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (minutes == 0) "$hours h" else "$hours h $minutes min"
}
