## MODIFIED Requirements

### Requirement: Inworld API error handling
The system SHALL handle Inworld API errors with clear error messages. HTTP 401 SHALL indicate invalid or expired credentials. HTTP 429 SHALL be retried with exponential backoff (up to 3 attempts with delays of 1s, 2s, 4s). If retries are exhausted, the system SHALL throw an `InworldRateLimitException`. HTTP 5xx (500–599) SHALL be treated as transient: it SHALL be retried with the same exponential backoff schedule (up to 3 attempts with delays of 1s, 2s, 4s), and if retries are exhausted the system SHALL throw an `InworldTransientException` including the HTTP status and response body. Other error status codes SHALL throw immediately with the HTTP status and response body in the error message.

Transient I/O failures (`ResourceAccessException`, e.g. connection reset or operation timed out) SHALL be retried with the same exponential backoff schedule (up to 3 attempts with delays of 1s, 2s, 4s). If retries are exhausted, the system SHALL rethrow the `ResourceAccessException`.

#### Scenario: Invalid credentials
- **WHEN** the Inworld API returns HTTP 401
- **THEN** the system throws an error indicating Inworld API credentials are invalid or expired

#### Scenario: Rate limit exceeded with successful retry
- **WHEN** the Inworld API returns HTTP 429 on the first attempt
- **AND** the retry succeeds on the second attempt
- **THEN** the system returns the successful response after a 1-second backoff delay

#### Scenario: Rate limit exceeded with exhausted retries
- **WHEN** the Inworld API returns HTTP 429 on all 3 retry attempts
- **THEN** the system throws an `InworldRateLimitException` indicating the rate limit was exceeded after retries

#### Scenario: Transient I/O error with successful retry
- **WHEN** the Inworld API request fails with a `ResourceAccessException` (e.g. `Connection reset`) on the first attempt
- **AND** the retry succeeds on the second attempt
- **THEN** the system returns the successful response after a 1-second backoff delay and logs a warning with the I/O error message

#### Scenario: Transient I/O error with exhausted retries
- **WHEN** the Inworld API request fails with a `ResourceAccessException` on all 3 retry attempts
- **THEN** the system rethrows the `ResourceAccessException` from the final attempt

#### Scenario: Transient 5xx error with successful retry
- **WHEN** the Inworld API returns HTTP 503 on the first attempt
- **AND** the retry succeeds on the second attempt
- **THEN** the system returns the successful response after a 1-second backoff delay and logs a warning

#### Scenario: Transient 5xx error with exhausted retries
- **WHEN** the Inworld API returns HTTP 503 on all 3 retry attempts
- **THEN** the system throws an `InworldTransientException` including the HTTP status and response body

#### Scenario: Non-retryable client error
- **WHEN** the Inworld API returns a non-401, non-429 HTTP 4xx error
- **THEN** the system logs the error body and throws an error with the HTTP status and body (no retry)

#### Scenario: Partial failure in parallel generation
- **WHEN** 6 chunks are generated in parallel and 1 chunk fails after retry exhaustion
- **THEN** the entire generation fails with the error from the failed chunk
