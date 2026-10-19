# Proposal

## Why

An LLM provider config can look fine in the settings page while the key the pipeline actually uses is empty or invalid. That only surfaces when a scheduled episode fails mid-run with "At least one credential source must be specified". A test button on the provider shows it immediately.

## What Changes

- New endpoint `POST /users/{userId}/api-keys/LLM/{provider}/test` that tests the LLM provider config exactly as the pipeline resolves it (stored config, else the environment fallback) and returns `{ success, message }`.
- The test fails without a request when no config resolves or the resolved key is empty (except for keyless `ollama`), and otherwise makes a free authenticated request: `GET /v1/key` for OpenRouter (whose `/v1/models` answers 200 for any key), `GET /v1/models` for every other provider.
- A "Test" button on each LLM provider row in Settings > API keys, showing the result as a toast.

## Capabilities

### New Capabilities

### Modified Capabilities
- `user-api-keys`: adds a requirement for testing a stored LLM provider config.

## Impact

- New `user/LlmProviderTestController`, `user/LlmProviderTestService`.
- `frontend/src/app/settings/page.tsx`: Test button on LLM rows.
- No schema or configuration changes.
