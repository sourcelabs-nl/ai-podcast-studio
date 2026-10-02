package com.aisummarypodcast.api

/**
 * The API's error contract. Each type maps to one HTTP status in [ApiExceptionHandler], and its
 * message is the complete text the client sees, so a throw site builds the final wording.
 */

/** The request is malformed or refers to something that cannot be acted on (400). */
class InvalidRequestException(message: String) : IllegalArgumentException(message)

/** The request is well-formed but its content is not acceptable (422). */
class UnprocessableRequestException(message: String) : IllegalArgumentException(message)

/** A third-party service the request depends on failed (502). */
class UpstreamServiceException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** The operation was accepted but failed on our side (500). */
class OperationFailedException(message: String, cause: Throwable) : RuntimeException(message, cause)

/** The resource does not exist or is not reachable by the caller, answered with an empty 404. */
class ResourceNotFoundException : RuntimeException("Resource not found")
