package com.aisummarypodcast.store

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.min

@Table("sources")
data class Source(
    @Id val id: String,
    val podcastId: String,
    val type: SourceType,
    val url: String,
    val pollIntervalMinutes: Int = 30,
    val enabled: Boolean = true,
    val lastPolled: String? = null,
    val lastSeenId: String? = null,
    val aggregate: Boolean? = null,
    val maxFailures: Int? = null,
    val maxBackoffHours: Int? = null,
    val pollDelaySeconds: Int? = null,
    val categoryFilter: String? = null,
    val label: String? = null,
    val createdAt: String = "1970-01-01T00:00:00Z",
    val consecutiveFailures: Int = 0,
    val lastFailureType: String? = null,
    val disabledReason: String? = null,
    @Version val version: Long? = null
) {

    /** Whether the source's effective poll interval has elapsed since it was last polled. */
    fun isDue(defaultMaxBackoffHours: Int, now: Instant = Instant.now()): Boolean =
        nextPollAt(defaultMaxBackoffHours)?.isBefore(now) ?: true

    /**
     * When the source is next due for a poll: its last poll plus the effective interval, which grows
     * with consecutive failures. Null for a source that has never polled, which is due immediately.
     */
    fun nextPollAt(defaultMaxBackoffHours: Int): Instant? {
        val lastPolled = lastPolled?.let { Instant.parse(it) } ?: return null
        return lastPolled.plus(effectivePollIntervalMinutes(defaultMaxBackoffHours), ChronoUnit.MINUTES)
    }

    /** The poll interval after exponential backoff for consecutive failures, capped at the max backoff. */
    fun effectivePollIntervalMinutes(defaultMaxBackoffHours: Int): Long {
        if (consecutiveFailures == 0) return pollIntervalMinutes.toLong()
        val maxBackoffMinutes = (maxBackoffHours ?: defaultMaxBackoffHours).toLong() * 60
        val backoff = pollIntervalMinutes.toLong() * (1L shl min(consecutiveFailures, 30))
        return min(backoff, maxBackoffMinutes)
    }
}
