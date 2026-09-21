# Capability: Source Polling Backoff

## Purpose

Exponential backoff and auto-disable logic for failing sources, including error classification, failure tracking, and configurable thresholds.

## Requirements

### Requirement: Error classification
The system SHALL classify polling errors into two categories: **transient** and **permanent**.

Transient errors are those the status or exception itself says to come back later for: HTTP 408
(request timeout), HTTP 429 (rate limit), HTTP 5xx (server errors), connection timeouts, socket
timeouts, and XML/RSS parse failures. All unrecognized exception types SHALL be classified as
transient.

Every other HTTP client error (4xx) SHALL be classified as permanent, whether or not the specific
status is enumerated, as SHALL DNS resolution failures (`UnknownHostException`). A client error
means the request was refused or malformed, which retrying does not change. Enumerating the
permanent statuses instead and defaulting to transient is what allowed a lapsed subscription
(`402 Payment Required`) to retry silently for hours while a podcast lost all of its content from
that source, with nothing recorded to say the source needed attention.

#### Scenario: HTTP 404 classified as permanent
- **WHEN** a source poll receives an HTTP 404 response
- **THEN** the failure is classified as permanent

#### Scenario: HTTP 402 classified as permanent
- **WHEN** a source poll receives an HTTP 402 response because the feed's subscription has lapsed
- **THEN** the failure is classified as permanent, so the source is recorded as needing attention
  rather than retried indefinitely

#### Scenario: HTTP 401 classified as permanent
- **WHEN** a source poll receives an HTTP 401 response
- **THEN** the failure is classified as permanent

#### Scenario: An unenumerated client error is classified as permanent
- **WHEN** a source poll receives a 4xx response whose status is not individually enumerated
- **THEN** the failure is classified as permanent

#### Scenario: HTTP 408 classified as transient
- **WHEN** a source poll receives an HTTP 408 response
- **THEN** the failure is classified as transient

#### Scenario: HTTP 429 classified as transient
- **WHEN** a source poll receives an HTTP 429 response
- **THEN** the failure is classified as transient

#### Scenario: HTTP 500 classified as transient
- **WHEN** a source poll receives an HTTP 500 response
- **THEN** the failure is classified as transient

#### Scenario: Connection timeout classified as transient
- **WHEN** a source poll times out connecting to the server
- **THEN** the failure is classified as transient

#### Scenario: DNS failure classified as permanent
- **WHEN** a source poll fails with `UnknownHostException`
- **THEN** the failure is classified as permanent

#### Scenario: Unknown exception classified as transient
- **WHEN** a source poll fails with an unexpected exception type
- **THEN** the failure is classified as transient

### Requirement: Failure tracking on source
The system SHALL track consecutive failures on each source. The `Source` entity SHALL have a `consecutiveFailures` field (default 0), a `lastFailureType` field (nullable, values: `"transient"` or `"permanent"`), and a `disabledReason` field (nullable). On each failed poll, `consecutiveFailures` SHALL be incremented by 1 and `lastFailureType` SHALL be set to the classified error type. On a successful poll, `consecutiveFailures` SHALL be reset to 0 and `lastFailureType` SHALL be cleared to null.

#### Scenario: First failure increments counter
- **WHEN** a source poll fails for the first time with a transient error
- **THEN** `consecutiveFailures` is set to 1 and `lastFailureType` is set to `"transient"`

#### Scenario: Repeated failures increment counter
- **WHEN** a source with `consecutiveFailures = 3` fails again
- **THEN** `consecutiveFailures` is set to 4

#### Scenario: Success resets failure tracking
- **WHEN** a source with `consecutiveFailures = 3` polls successfully
- **THEN** `consecutiveFailures` is set to 0 and `lastFailureType` is set to null

### Requirement: Exponential backoff for failed sources
The system SHALL apply exponential backoff when deciding when to next poll a source that has consecutive failures. The backoff delay SHALL be computed as `pollIntervalMinutes × 2^consecutiveFailures`, capped at `app.source.max-backoff-hours` (default: 24) converted to minutes. The scheduler SHALL skip a source if the time since `lastPolled` is less than the computed backoff delay. Sources with `consecutiveFailures = 0` SHALL use the normal `pollIntervalMinutes`.

#### Scenario: First failure doubles the poll interval
- **WHEN** a source with `pollIntervalMinutes = 60` has `consecutiveFailures = 1`
- **THEN** the next poll is scheduled 120 minutes after `lastPolled`

#### Scenario: Third failure results in 8x interval
- **WHEN** a source with `pollIntervalMinutes = 60` has `consecutiveFailures = 3`
- **THEN** the next poll is scheduled 480 minutes (8 hours) after `lastPolled`

#### Scenario: Backoff capped at maximum
- **WHEN** a source with `pollIntervalMinutes = 60` has `consecutiveFailures = 5`
- **THEN** the next poll is scheduled 1440 minutes (24 hours) after `lastPolled`, not 1920 minutes

#### Scenario: Successful poll restores normal interval
- **WHEN** a source with `consecutiveFailures = 0` and `pollIntervalMinutes = 60` is due for polling
- **THEN** the scheduler polls it 60 minutes after `lastPolled` (normal interval, no backoff)

### Requirement: Auto-disable after permanent failures
The system SHALL auto-disable a source after `app.source.max-failures` (default: 15) consecutive **permanent** failures. When the threshold is reached, the source's `enabled` field SHALL be set to `false` and `disabledReason` SHALL be set to a human-readable message including the error type (e.g. `"Auto-disabled after 15 consecutive 404 errors"`). Transient failures SHALL NOT count toward the auto-disable threshold — only permanent failures trigger auto-disable.

#### Scenario: Source disabled after 15 permanent failures
- **WHEN** a source reaches 15 consecutive permanent failures (e.g. HTTP 404)
- **THEN** the source is set to `enabled = false` with `disabledReason = "Auto-disabled after 15 consecutive 404 errors"`

#### Scenario: Transient failures do not trigger auto-disable
- **WHEN** a source has 20 consecutive transient failures (e.g. timeouts)
- **THEN** the source remains `enabled = true` (only permanent failures count toward auto-disable)

#### Scenario: Mixed failures — permanent counter resets on transient
- **WHEN** a source has 3 consecutive permanent failures followed by a transient failure
- **THEN** the permanent failure count is not considered reset — `consecutiveFailures` continues to increment, but auto-disable only triggers when the last N failures are ALL permanent

### Requirement: Reset failure state on re-enable
The system SHALL reset all failure tracking fields when a source is re-enabled via the API. Specifically, when `enabled` is set from `false` to `true`, the system SHALL set `consecutiveFailures` to 0, `lastFailureType` to null, and `disabledReason` to null.

#### Scenario: User re-enables a disabled source
- **WHEN** a user updates a source with `enabled = false` and `disabledReason = "Auto-disabled after 5 consecutive 404 errors"` to `enabled = true`
- **THEN** `consecutiveFailures` is set to 0, `lastFailureType` is set to null, and `disabledReason` is set to null

### Requirement: Configurable failure thresholds
The system SHALL support the following configuration properties:
- `app.source.max-failures` (default: 15) — number of consecutive permanent failures before auto-disable
- `app.source.max-backoff-hours` (default: 24) — maximum backoff interval cap in hours

#### Scenario: Custom max-failures threshold
- **WHEN** `app.source.max-failures` is set to 3 and a source reaches 3 consecutive permanent failures
- **THEN** the source is auto-disabled

#### Scenario: Custom max-backoff-hours
- **WHEN** `app.source.max-backoff-hours` is set to 6 and a source has `consecutiveFailures = 10` with `pollIntervalMinutes = 60`
- **THEN** the backoff is capped at 360 minutes (6 hours), not 24 hours

### Requirement: Feed fetch failures preserve their HTTP status

The system SHALL retrieve RSS and YouTube feeds over an HTTP client that raises status-bearing exceptions (`HttpClientErrorException` / `HttpServerErrorException`), so that the HTTP status of a failed fetch reaches `PollFailure.classify` rather than being flattened into a generic `IOException` whose status survives only in its message.

Without this, every RSS and YouTube poll failure classifies as transient regardless of the server's response, which silently defeats both auto-disable and any permanent/transient distinction downstream of classification.

Feed requests SHALL send an explicit `User-Agent` identifying the application, matching the one used by the deep-fetch path.

#### Scenario: RSS feed returning 403 classifies as permanent
- **WHEN** an RSS source's feed URL responds with HTTP 403
- **THEN** the poll failure is classified as permanent and `lastFailureType` is set to `"permanent"`

#### Scenario: RSS feed returning 404 classifies as permanent
- **WHEN** an RSS source's feed URL responds with HTTP 404
- **THEN** the poll failure is classified as permanent

#### Scenario: RSS feed returning 500 classifies as transient
- **WHEN** an RSS source's feed URL responds with HTTP 500
- **THEN** the poll failure is classified as transient

#### Scenario: RSS feed returning 429 classifies as transient
- **WHEN** an RSS source's feed URL responds with HTTP 429
- **THEN** the poll failure is classified as transient

#### Scenario: Malformed feed body still classifies as transient
- **WHEN** an RSS source's feed URL responds with HTTP 200 and a body that is not valid XML
- **THEN** the poll failure is classified as transient, as an XML parse failure

#### Scenario: Feed requests identify the application
- **WHEN** the system fetches any RSS or YouTube feed
- **THEN** the request carries a `User-Agent` header identifying the application

#### Scenario: Feed behind a redirect is followed
- **WHEN** an RSS source's feed URL responds with HTTP 301 pointing at another URL serving the feed
- **THEN** the redirect is followed and the feed's entries are returned, as they were before feed retrieval moved to a status-bearing HTTP client

#### Scenario: Feed encoding detection is preserved
- **WHEN** a feed is served with a non-UTF-8 charset declared in its `Content-Type` header or XML declaration
- **THEN** the feed is decoded using that charset, as it was before feed retrieval moved to a status-bearing HTTP client
