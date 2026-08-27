package com.aisummarypodcast.source

import com.aisummarypodcast.store.Source

internal fun CreateSourceRequest.toConfig() = SourceConfig(
    pollIntervalMinutes = pollIntervalMinutes, enabled = enabled, aggregate = aggregate,
    maxFailures = maxFailures, maxBackoffHours = maxBackoffHours, pollDelaySeconds = pollDelaySeconds,
    categoryFilter = categoryFilter, label = label
)

internal fun UpdateSourceRequest.toConfig() = SourceConfig(
    pollIntervalMinutes = pollIntervalMinutes, enabled = enabled, aggregate = aggregate,
    maxFailures = maxFailures, maxBackoffHours = maxBackoffHours, pollDelaySeconds = pollDelaySeconds,
    categoryFilter = categoryFilter, label = label
)

internal fun Source.toResponse() = SourceResponse(
    id = id, podcastId = podcastId, type = type.value, url = url,
    pollIntervalMinutes = pollIntervalMinutes, enabled = enabled, aggregate = aggregate,
    maxFailures = maxFailures, maxBackoffHours = maxBackoffHours, pollDelaySeconds = pollDelaySeconds,
    categoryFilter = categoryFilter, label = label, createdAt = createdAt, lastPolled = lastPolled, lastSeenId = lastSeenId,
    consecutiveFailures = consecutiveFailures, lastFailureType = lastFailureType,
    disabledReason = disabledReason
)

internal fun SourceResponse.withBreakerState(state: HostBreakerState?) = copy(
    host = state?.host,
    hostSourceCount = state?.sourceCount ?: 0,
    hostBreakerOpen = state?.open ?: false
)
