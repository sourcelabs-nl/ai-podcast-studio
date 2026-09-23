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

/**
 * The episode cannot be re-run because it does not carry the article window it was generated for
 * (maps to 409).
 *
 * A re-run reproduces a past period, which is only defined when the episode recorded its window.
 * Episodes generated before the window was recorded have to be generated afresh instead.
 */
class EpisodeNotRerunnableException(message: String) : IllegalStateException(message)

/**
 * The episode cannot be recomposed with review feedback because it is not a focus episode awaiting
 * review (maps to 409).
 */
class EpisodeNotRecomposableException(message: String) : IllegalStateException(message)

/**
 * The episode cannot be discarded because it still has a publication in PUBLISHED status
 * (maps to 409). It has to be unpublished from every target first.
 */
class EpisodePublishedException(message: String) : IllegalStateException(message)

/** An experiment request no run could execute, such as an unknown stage name (maps to 400). */
class InvalidExperimentException(message: String) : IllegalArgumentException(message)

/**
 * An experiment request asking for more runs than `app.experiments.max-variant-repeats` allows
 * (maps to 400). Refused before any run starts, so it costs nothing.
 */
class ExperimentTooLargeException(message: String) : IllegalArgumentException(message)

/**
 * The episode cannot be experimented on: it has no linked articles to recompose, or it is itself an
 * experiment episode (maps to 409).
 */
class EpisodeNotExperimentableException(message: String) : IllegalStateException(message)
