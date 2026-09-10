package com.aisummarypodcast.util

import org.springframework.core.NestedExceptionUtils
import java.sql.SQLException

// Covers every SQLite constraint kind (UNIQUE, NOT NULL, FOREIGN KEY, CHECK). Which one was broken
// follows from the statement that raised it.
private const val SQLITE_CONSTRAINT_ERROR_CODE = 19

/**
 * Whether [e] is a SQLite constraint violation.
 *
 * SQLite's exception translator leaves constraint failures uncategorized: they arrive as
 * `UncategorizedSQLException`, never as `DataIntegrityViolationException`, so catching the Spring
 * type silently never matches. The SQLite error code is inspected directly instead.
 *
 * Callers must know which constraint their statement can break, because this does not distinguish
 * between kinds. Use it only where a specific violation is expected and recoverable, and let
 * anything else propagate.
 */
fun isConstraintViolation(e: RuntimeException): Boolean {
    val cause = NestedExceptionUtils.getMostSpecificCause(e)
    return cause is SQLException && cause.errorCode == SQLITE_CONSTRAINT_ERROR_CODE
}
