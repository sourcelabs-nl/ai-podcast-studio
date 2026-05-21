# Capability: User API Keys

## Purpose

Encrypted per-user provider configuration storage for LLM and TTS providers, with configurable base URLs and fallback to global environment variables.
## Requirements
### Requirement: Provider credentials encrypted at rest
The system SHALL encrypt API keys before storing them in the database using AES-256-GCM. Each key SHALL be encrypted with a unique random IV (initialization vector). The combined `IV + ciphertext` SHALL be Base64-encoded and stored in the `encrypted_api_key` column. The master encryption key SHALL be provided via the `app.encryption.master-key` application property.

#### Scenario: Master key configured
- **WHEN** the application starts with `app.encryption.master-key` set (e.g., via `APP_ENCRYPTION_MASTER_KEY` environment variable)
- **THEN** the application starts successfully and can encrypt/decrypt API keys

#### Scenario: Master key not configured
- **WHEN** the application starts without `app.encryption.master-key` configured
- **THEN** the application fails to start with a clear error message indicating the master encryption key is required

#### Scenario: Each key has unique IV
- **WHEN** two API keys are stored for the same provider by different users
- **THEN** each stored value uses a different random IV, producing different ciphertext even if the API keys happen to be identical

### Requirement: Provider default base URLs
The system SHALL maintain a map of known provider names to default base URLs. When a user configures a provider without an explicit `base_url`, the system SHALL use the default for that provider. Known defaults SHALL be: `openrouter` → `https://openrouter.ai/api`, `openai` → `https://api.openai.com`, `ollama` → `http://localhost:11434/v1`, `elevenlabs` → `https://api.elevenlabs.io`, `inworld` → `https://api.inworld.ai`. If the provider is not in the known list and no `base_url` is provided, the system SHALL reject the request with HTTP 400. For `PUBLISHING` category providers, the `base_url` requirement SHALL be skipped (always null).

#### Scenario: Known provider without explicit base URL
- **WHEN** a `PUT /users/{userId}/api-keys/{category}` request is received with provider `"ollama"` and no `baseUrl`
- **THEN** the system stores the config with the default base URL `http://localhost:11434/v1`

#### Scenario: ElevenLabs provider without explicit base URL
- **WHEN** a `PUT /users/{userId}/api-keys/TTS` request is received with provider `"elevenlabs"` and no `baseUrl`
- **THEN** the system stores the config with the default base URL `https://api.elevenlabs.io`

#### Scenario: Unknown provider without base URL for LLM/TTS
- **WHEN** a `PUT /users/{userId}/api-keys/LLM` request is received with provider `"azure"` and no `baseUrl`
- **THEN** the system returns HTTP 400 with an error indicating a base URL is required for unknown providers

#### Scenario: Publishing provider without base URL
- **WHEN** a `PUT /users/{userId}/api-keys/PUBLISHING` request is received with provider `"ftp"` and no `baseUrl`
- **THEN** the system stores the config with a null `base_url` (no validation error)

#### Scenario: Known provider with explicit base URL override
- **WHEN** a `PUT /users/{userId}/api-keys/{category}` request is received with provider `"ollama"` and `baseUrl` set to `http://192.168.1.100:11434/v1`
- **THEN** the system stores the config with the explicitly provided base URL

### Requirement: Store API key for a provider
The system SHALL store per-user provider configurations in a `user_provider_configs` table with columns: `user_id` (TEXT, FK to users), `provider` (TEXT, NOT NULL), `category` (TEXT, NOT NULL), `base_url` (TEXT, nullable), `encrypted_api_key` (TEXT, nullable, Base64-encoded IV+ciphertext), with a composite primary key of `(user_id, category, provider)`. Supported category values SHALL be `"LLM"`, `"TTS"`, and `"PUBLISHING"`. The `provider` field SHALL identify the external service (e.g., `"openrouter"`, `"openai"`, `"ollama"`, `"ftp"`, `"soundcloud"`). The API key SHALL be encrypted before storage and decrypted only when needed at runtime. The API key MAY be null for providers that do not require authentication (e.g., Ollama). For `PUBLISHING` category providers, the `api_key` value SHALL be a JSON-encoded string containing all credential fields for that provider. The `base_url` column SHALL be null for publishing providers. Each user MAY have multiple configurations per category, but SHALL have at most one configuration per category-provider combination.

#### Scenario: Set a provider config with API key
- **WHEN** a `PUT /users/{userId}/api-keys/{category}` request is received with a JSON body containing `provider` and `apiKey`, and `category` is a valid value (`LLM`, `TTS`, or `PUBLISHING`)
- **THEN** the system encrypts the API key with AES-256-GCM, stores it with the provider and category in the `user_provider_configs` table, and returns HTTP 200

#### Scenario: Set a provider config without API key
- **WHEN** a `PUT /users/{userId}/api-keys/{category}` request is received with `provider` set to `"ollama"` and no `apiKey`
- **THEN** the system stores the config with a null `encrypted_api_key` and returns HTTP 200

#### Scenario: Set API key with invalid category
- **WHEN** a `PUT /users/{userId}/api-keys/{category}` request is received with a `category` that is not `LLM`, `TTS`, or `PUBLISHING`
- **THEN** the system returns HTTP 400 with a validation error message indicating the valid categories

#### Scenario: Set publishing credentials for FTP
- **WHEN** a `PUT /users/{userId}/api-keys/PUBLISHING` request is received with `provider = "ftp"` and `apiKey` containing JSON-encoded FTP credentials
- **THEN** the system encrypts and stores the JSON string, and returns HTTP 200

#### Scenario: Set API key for non-existing user
- **WHEN** a `PUT /users/{userId}/api-keys/{category}` request is received for a user that does not exist
- **THEN** the system returns HTTP 404

#### Scenario: Set API key with missing provider
- **WHEN** a `PUT /users/{userId}/api-keys/{category}` request is received without `provider` in the body
- **THEN** the system returns HTTP 400 with a validation error message

#### Scenario: Replace an existing provider config for a category and provider
- **WHEN** a `PUT /users/{userId}/api-keys/{category}` request is received for a category and provider combination that already has a config stored
- **THEN** the system updates the base URL and encrypted key for that specific provider, leaving other providers in the same category unchanged

#### Scenario: Add a second provider to the same category
- **WHEN** a user already has an LLM config for provider `"openrouter"` and a `PUT /users/{userId}/api-keys/LLM` request is received with provider `"ollama"`
- **THEN** the system stores the new config alongside the existing one, and both configs are returned by the list endpoint

### Requirement: List API key providers for a user
The system SHALL provide an endpoint to list which provider configurations a user has set up. The response SHALL include the `category`, `provider`, and `baseUrl` (resolved, with default applied if stored value is null) for each config — NOT the actual key values or encrypted values. When a user has multiple providers for the same category, all SHALL be returned.

#### Scenario: List configs with multiple providers per category
- **WHEN** a `GET /users/{userId}/api-keys` request is received for a user with two LLM configs (`"openrouter"` and `"ollama"`) and one TTS config (`"openai"`)
- **THEN** the system returns HTTP 200 with a JSON array containing three entries

#### Scenario: List configs when none configured
- **WHEN** a `GET /users/{userId}/api-keys` request is received for a user with no provider configs
- **THEN** the system returns HTTP 200 with an empty JSON array

#### Scenario: List configs for non-existing user
- **WHEN** a `GET /users/{userId}/api-keys` request is received for a user that does not exist
- **THEN** the system returns HTTP 404

### Requirement: Delete an API key
The system SHALL allow deleting a user's provider configuration for a specific category and provider combination. The delete endpoint SHALL target a single provider within a category, not all providers in a category.

#### Scenario: Delete existing config by category and provider
- **WHEN** a `DELETE /users/{userId}/api-keys/{category}/{provider}` request is received for a user that has a config for that category and provider
- **THEN** the system deletes that specific config record and returns HTTP 204, leaving other providers in the same category unchanged

#### Scenario: Delete non-existing config by category and provider
- **WHEN** a `DELETE /users/{userId}/api-keys/{category}/{provider}` request is received for a category and provider combination that has no config stored
- **THEN** the system returns HTTP 404

#### Scenario: Delete with invalid category
- **WHEN** a `DELETE /users/{userId}/api-keys/{category}/{provider}` request is received with a `category` that is not `LLM`, `TTS`, or `PUBLISHING`
- **THEN** the system returns HTTP 400 with a validation error message

### Requirement: API key resolution with fallback
When the pipeline needs a provider configuration, the system SHALL resolve it by category and provider name. The `resolveConfig` method SHALL accept a `userId`, `category`, and `provider` parameter. It SHALL look up the user's config for that specific `(userId, category, provider)` combination. If found, it SHALL decrypt the API key (if present) and resolve the base URL (stored value or provider default). If no user config exists for the requested provider, it SHALL fall back to the corresponding environment variable only if the provider matches a known fallback provider (`"openrouter"` for `LLM` → `OPENROUTER_API_KEY`, `"openai"` for `TTS` → `OPENAI_API_KEY`, `"elevenlabs"` for `TTS` → `ELEVENLABS_API_KEY`). The resolution SHALL return a `ProviderConfig` containing `baseUrl: String` and `apiKey: String?`. If neither a user config nor applicable env var fallback exists, the resolution SHALL fail with a clear error indicating which provider config is missing. The existing no-provider overload (resolving by category only, picking the first config) SHALL be retained for backward compatibility with TTS resolution.

#### Scenario: User has config for the requested provider
- **WHEN** the pipeline resolves config for provider "openrouter" and the user has a config for (LLM, "openrouter")
- **THEN** the system returns that config's base URL and decrypted API key

#### Scenario: User has config for a different provider only
- **WHEN** the pipeline resolves config for provider "ollama" but the user only has a config for (LLM, "openrouter")
- **THEN** the system does not use the openrouter config and checks the global fallback

#### Scenario: Provider matches global fallback
- **WHEN** the pipeline resolves config for provider "openrouter", the user has no config for openrouter, but `OPENROUTER_API_KEY` env var is set
- **THEN** the system returns the default openrouter base URL with the env var as API key

#### Scenario: Provider does not match global fallback
- **WHEN** the pipeline resolves config for provider "ollama" and the user has no config for ollama
- **THEN** the system fails with an error indicating no provider config is available for "ollama"

#### Scenario: ElevenLabs matches global fallback
- **WHEN** the pipeline resolves config for provider "elevenlabs", the user has no config for elevenlabs, but `ELEVENLABS_API_KEY` env var is set
- **THEN** the system returns the default ElevenLabs base URL with the env var as API key

#### Scenario: TTS resolution unchanged
- **WHEN** the TTS pipeline resolves config using the category-only method (no provider parameter)
- **THEN** the system picks the first available TTS config for the user, unchanged from current behavior

#### Scenario: Spring AI auto-configuration excluded
- **WHEN** the application starts
- **THEN** no Spring AI auto-configured chat or audio beans are created, and no `spring.ai` properties are required

### Requirement: Cascade delete API keys with user
When a user is deleted, all of the user's provider configurations SHALL be deleted as part of the cascade.

#### Scenario: Delete user removes provider configs
- **WHEN** a user with 2 stored provider configs is deleted
- **THEN** both config records are removed from the `user_provider_configs` table

### Requirement: Migrate existing API keys to categories
A Flyway migration SHALL rename the `user_api_keys` table to `user_provider_configs`, add the `base_url` column (nullable), and make `encrypted_api_key` nullable. Existing data SHALL be preserved.

#### Scenario: Migration renames table and adds column
- **WHEN** the Flyway migration runs on a database with existing rows in `user_api_keys`
- **THEN** the table is renamed to `user_provider_configs`, the `base_url` column is added (null for all existing rows), `encrypted_api_key` becomes nullable, and all existing data is preserved

#### Scenario: Migration on empty table
- **WHEN** the Flyway migration runs on a database with no rows in `user_api_keys`
- **THEN** the migration completes successfully with the new table structure

### Requirement: Migrate to composite primary key
A Flyway migration SHALL change the `user_provider_configs` primary key from `(user_id, category)` to `(user_id, category, provider)`. Existing data SHALL be preserved. Since SQLite does not support altering primary keys, the migration SHALL recreate the table with the new key and copy existing data.

#### Scenario: Migration preserves existing data
- **WHEN** the Flyway migration runs on a database with existing rows in `user_provider_configs`
- **THEN** all existing rows are preserved in the new table with the `(user_id, category, provider)` primary key

#### Scenario: Migration on empty table
- **WHEN** the Flyway migration runs on a database with no rows in `user_provider_configs`
- **THEN** the migration completes successfully with the new table structure

### Requirement: OAuth connections are separate from API key configs
The system SHALL store OAuth-based external service connections (e.g., SoundCloud) in a separate `oauth_connections` table, not in the `user_provider_configs` table. The existing API key resolution, provider defaults, and fallback mechanisms SHALL remain unchanged and SHALL NOT interact with OAuth connections.

#### Scenario: OAuth connection does not appear in API key listing
- **WHEN** a `GET /users/{userId}/api-keys` request is received for a user who has a SoundCloud OAuth connection
- **THEN** the SoundCloud connection is NOT included in the response — only `user_provider_configs` entries are returned

#### Scenario: Existing API key operations unaffected
- **WHEN** a user has both API key configs (LLM, TTS) and an OAuth connection (SoundCloud)
- **THEN** all existing API key CRUD operations and pipeline resolution work exactly as before

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

