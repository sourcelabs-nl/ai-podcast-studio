## ADDED Requirements

### Requirement: A stored LLM provider config can be tested
The system SHALL expose `POST /users/{userId}/api-keys/LLM/{provider}/test`, which tests the config
the pipeline would use for that user and provider: the user's stored config, or the environment
fallback when none is stored. The response SHALL be `{ success, message }` with HTTP 200 for every
test outcome, and 404 when the user does not exist. When no config resolves, or the resolved key is
empty for a provider other than `ollama`, the test SHALL fail without making a request and SHALL
say so. Otherwise it SHALL make one authenticated request that consumes no tokens: `GET /v1/key`
for `openrouter`, `GET /v1/models` for any other provider. A 2xx response is a success; any other
status, a timeout or a connection error is a failure whose message names the cause. The API key
SHALL never appear in the response or the logs.

#### Scenario: A valid key passes
- **WHEN** the stored OpenRouter key is accepted by `GET /v1/key`
- **THEN** the response is `success: true`

#### Scenario: An empty stored key fails without a request
- **WHEN** the stored OpenRouter config decrypts to an empty key
- **THEN** the response is `success: false` with a message saying the stored key is empty, and no request is made

#### Scenario: No config fails without a request
- **WHEN** the user has no OpenRouter config and no environment fallback is set
- **THEN** the response is `success: false` with a message saying no config is available

#### Scenario: A rejected key fails
- **WHEN** the provider answers 401
- **THEN** the response is `success: false` and the message contains the status

#### Scenario: An unknown user
- **WHEN** the user does not exist
- **THEN** the response is 404

#### Scenario: Test button in settings
- **WHEN** the user clicks Test on an LLM provider row in Settings > API keys
- **THEN** the button shows progress, the result appears as a toast, and the row shows "Connected" (green) or "Failed" (red) with the message on hover until the next test or page reload
