package com.aisummarypodcast.podcast

/**
 * Typed podcast failures so [PodcastExceptionHandler] can map each to an HTTP status by type
 * instead of sniffing exception messages (Rule SB8). Extends [IllegalStateException] so existing
 * `catch (e: IllegalStateException)` sites keep working.
 */

/**
 * The episode cannot be regenerated because it has no linked articles (maps to 409).
 *
 * Regeneration recomposes from the source episode's `episode_articles` rows, so an episode that
 * failed before article selection can never be regenerated — only generated afresh.
 */
class EpisodeNotRegenerableException(message: String) : IllegalStateException(message)
