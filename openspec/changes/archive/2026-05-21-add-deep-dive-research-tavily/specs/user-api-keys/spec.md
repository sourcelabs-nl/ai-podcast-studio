## ADDED Requirements

### Requirement: RESEARCH API key category

The `ApiKeyCategory` enum SHALL include a `RESEARCH` value. The API SHALL accept `PUT /users/{userId}/api-keys/research` and `DELETE /users/{userId}/api-keys/research` for storing and removing the user's Tavily API key. Stored keys MUST be encrypted on disk like all other categories.

#### Scenario: Tavily key is stored encrypted

- **WHEN** a client calls `PUT /users/{userId}/api-keys/research` with `{"provider":"tavily","apiKey":"tvly-abc"}`
- **THEN** the configuration row is persisted with the API key encrypted at rest

#### Scenario: Research category lists alongside LLM and TTS

- **WHEN** a client calls `GET /users/{userId}/api-keys`
- **THEN** the response includes the research category entry if configured

### Requirement: Research key resolves with environment fallback

`UserProviderConfigService` SHALL resolve the Tavily key from the user's stored configuration first, then fall back to the `TAVILY_API_KEY` environment variable when none is configured.

#### Scenario: Env fallback used

- **WHEN** no Tavily key is configured for the user but `TAVILY_API_KEY` is set
- **THEN** `resolveConfig(userId, RESEARCH, "tavily")` returns a config whose `apiKey` equals the env value

#### Scenario: User key takes precedence

- **WHEN** both a user-configured key and `TAVILY_API_KEY` exist
- **THEN** `resolveConfig` returns the user-configured key
