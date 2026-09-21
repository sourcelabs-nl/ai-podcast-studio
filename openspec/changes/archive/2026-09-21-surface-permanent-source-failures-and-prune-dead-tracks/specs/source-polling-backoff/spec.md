## MODIFIED Requirements

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
