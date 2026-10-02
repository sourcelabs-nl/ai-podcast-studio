package com.aisummarypodcast.llm

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** How long to wait before the first lookup, between lookups, and in total before giving up. */
data class GenerationStatsRetry(
    val initialDelay: Duration = 5.seconds,
    val interval: Duration = 10.seconds,
    val giveUpAfter: Duration = 3.minutes
)
