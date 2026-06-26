package com.aisummarypodcast.publishing

/**
 * Typed publishing failures so [PublishingExceptionHandler] can map each to an HTTP status by type
 * instead of sniffing exception messages (Rule SB8). All extend [IllegalStateException] so existing
 * `catch (e: IllegalStateException)` sites keep working and the generic handler stays a safe fallback.
 */

/** Publication target is missing or disabled for the podcast (maps to 400). */
class TargetNotConfiguredException(message: String) : IllegalStateException(message)

/** SoundCloud OAuth credentials are missing or expired and need re-authorization (maps to 401). */
class OAuthExpiredException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

/** Episode requires publish approval before it can be published (maps to 409). */
class PublishApprovalRequiredException(message: String) : IllegalStateException(message)

/** Episode is already published to the target (maps to 409). */
class AlreadyPublishedException(message: String) : IllegalStateException(message)

/** No publication exists for the requested episode/target (maps to 404). */
class NoPublicationFoundException(message: String) : IllegalStateException(message)
