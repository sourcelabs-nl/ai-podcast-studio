## MODIFIED Requirements

### Requirement: Inworld API client with Basic authentication
The system SHALL provide an `InworldApiClient` that authenticates to the Inworld AI API using HTTP Basic authentication. The client SHALL accept credentials in the format `key:secret` (stored as the `apiKey` in provider config), base64-encode them, and send the result in the `Authorization` header as `Basic <base64-encoded-credentials>`. Per-user credentials SHALL be resolved via `UserProviderConfigService` with `ApiKeyCategory.TTS` and provider name `"inworld"`, falling back to the global env vars (`INWORLD_AI_JWT_KEY` and `INWORLD_AI_JWT_SECRET` combined as `key:secret`).

The HTTP client SHALL use a response timeout of 5 minutes to accommodate long TTS generation requests (Inworld may take over 30 seconds for large scripts).

The HTTP client SHALL use a dedicated Reactor Netty connection provider with a maximum idle time of 30 seconds, so that pooled keep-alive connections are evicted before Inworld's load balancer closes them server-side. Reusing a connection that the server has already closed results in a `Connection reset` failure.

#### Scenario: Authentication with per-user config
- **WHEN** a user has configured Inworld TTS provider credentials (e.g. via `PUT /users/{userId}/api-keys/tts` with provider `"inworld"` and apiKey `"my-key:my-secret"`)
- **THEN** the client base64-encodes the credentials and sends them as `Authorization: Basic <token>`

#### Scenario: Authentication with global env vars
- **WHEN** no per-user Inworld config exists and `INWORLD_AI_JWT_KEY` and `INWORLD_AI_JWT_SECRET` env vars are set
- **THEN** the client uses the global credentials (combined as `key:secret`) to authenticate

#### Scenario: Missing credentials
- **WHEN** neither per-user config nor global env vars provide Inworld credentials
- **THEN** the client throws an `IllegalStateException` with a message indicating Inworld API credentials must be configured

#### Scenario: Response timeout for long scripts
- **WHEN** the Inworld API takes longer than the default HTTP client timeout (e.g. 30+ seconds for a large dialogue script)
- **THEN** the request does not time out because the client uses a 5-minute response timeout

#### Scenario: Idle pooled connections are evicted
- **WHEN** a pooled connection to the Inworld API has been idle for more than 30 seconds
- **THEN** the connection provider evicts it so a subsequent request opens a fresh connection instead of reusing a stale one

### Requirement: Inworld API error handling
The system SHALL handle Inworld API errors with clear error messages. HTTP 401 SHALL indicate invalid or expired credentials. HTTP 429 SHALL be retried with exponential backoff (up to 3 attempts with delays of 1s, 2s, 4s). If retries are exhausted, the system SHALL throw an `InworldRateLimitException`. Other error status codes SHALL include the HTTP status and response body in the error message.

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

#### Scenario: Generic API error
- **WHEN** the Inworld API returns HTTP 500
- **THEN** the system logs the error body and throws an error with the HTTP status and body (no retry)

#### Scenario: Partial failure in parallel generation
- **WHEN** 6 chunks are generated in parallel and 1 chunk fails after retry exhaustion
- **THEN** the entire generation fails with the error from the failed chunk
