package com.aisummarypodcast.podcast

import java.time.Instant

/**
 * The half-open article window `[start, end)` an episode is generated for. Instants, so the window
 * is unambiguous regardless of the podcast's timezone or a DST transition inside it.
 */
data class EpisodeWindow(
    val start: Instant,
    val end: Instant
) {
    val startIso: String get() = start.toString()
    val endIso: String get() = end.toString()

    override fun toString(): String = "[$startIso, $endIso)"
}
