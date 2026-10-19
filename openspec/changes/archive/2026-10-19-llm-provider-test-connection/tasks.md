# Tasks

## 1. Backend

- [x] 1.1 Add `LlmProviderTestService.test(userId, provider)`: resolve via `UserProviderConfigService.resolveConfig`, fail early on missing config or empty key (except ollama), else `GET /v1/key` (openrouter) or `/v1/models`; map status/errors to `TestConnectionResult`. Verify with `LlmProviderTestServiceTest` (MockRestServiceServer): success, 401, empty key, no config, ollama without key, server error.
- [x] 1.2 Add `LlmProviderTestController` at `POST /users/{userId}/api-keys/LLM/{provider}/test` delegating to the service, 404 for unknown user. Verify with `LlmProviderTestControllerTest`; `mvn test` passes.

## 2. Frontend

- [x] 2.1 Add a Test button with spinner to LLM rows in `settings/page.tsx`, toasting the result. Verify with `npx tsc --noEmit`.

## 3. Live check

- [x] 3.1 Restart the app and click Test on the OpenRouter row; confirm the result matches the known state of the key.
