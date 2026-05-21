# Capability: LLM Cache

## Purpose

Persistent caching layer for LLM responses to eliminate redundant API calls when identical prompts are sent to the same model, e.g. when multiple podcasts summarize the same article.
## Requirements
### Requirement: Persistent LLM response cache
The system SHALL maintain a persistent cache of LLM responses in SQLite. The cache table `llm_cache` SHALL have columns: `id` (INTEGER PRIMARY KEY AUTOINCREMENT), `prompt_hash` (TEXT, NOT NULL), `model` (TEXT, NOT NULL), `response` (TEXT), `created_at` (TEXT, ISO-8601), `input_tokens` (INTEGER, nullable), and `output_tokens` (INTEGER, nullable). A unique constraint SHALL exist on `(prompt_hash, model)`. The cache SHALL be created via a Flyway migration (V5), with token columns added in a subsequent migration (V11).

#### Scenario: Cache table exists after migration
- **WHEN** the application starts with the Flyway migrations applied
- **THEN** the `llm_cache` table exists with columns `id`, `prompt_hash`, `model`, `response`, `created_at`, `input_tokens`, and `output_tokens`, with a unique constraint on `(prompt_hash, model)`

### Requirement: Cache key derivation
The cache key SHALL be the SHA-256 hash of the string `"{model}:{promptText}"` where `model` is the LLM model identifier and `promptText` is the full user message text sent to the model. The composite key `(prompt_hash, model)` SHALL uniquely identify a cache entry.

#### Scenario: Same prompt and model produces cache hit
- **WHEN** an LLM call is made with prompt "Summarize: article text" and model "anthropic/claude-3-haiku"
- **AND** a cache entry exists with the same prompt hash and model
- **THEN** the cached response is returned without calling the LLM API

#### Scenario: Same prompt with different model produces cache miss
- **WHEN** an LLM call is made with prompt "Summarize: article text" and model "anthropic/claude-sonnet-4-20250514"
- **AND** a cache entry exists for the same prompt but model "anthropic/claude-3-haiku"
- **THEN** the LLM API is called and a new cache entry is stored

### Requirement: Transparent caching via ChatModel decorator
The system SHALL implement caching as a `CachingChatModel` that decorates the `OpenAiChatModel`. The decorator SHALL implement the `ChatModel` interface and be injected via `ChatClientFactory`. Existing pipeline components (`RelevanceFilter`, `ArticleSummarizer`, `BriefingComposer`) SHALL NOT require code changes.

#### Scenario: ChatClientFactory wraps model with cache
- **WHEN** `ChatClientFactory.createForPodcast()` creates a ChatClient
- **THEN** the underlying ChatModel is a `CachingChatModel` wrapping the `OpenAiChatModel`

#### Scenario: Pipeline components work without modification
- **WHEN** `ArticleSummarizer` calls `chatClient.prompt().user(prompt).call().content()`
- **THEN** the call is transparently routed through `CachingChatModel` which checks the cache first

### Requirement: Cache miss stores response and token usage
The system SHALL store the LLM response text and token usage in the cache after a cache miss. The `created_at` field SHALL be set to the current UTC timestamp in ISO-8601 format. The `input_tokens` and `output_tokens` fields SHALL be populated from the delegate response's usage metadata (`promptTokens` and `completionTokens`). If usage metadata is not available, token fields SHALL be null. Subsequent calls with the same prompt hash and model SHALL return the cached response with cached token counts.

#### Scenario: First call for a prompt stores result with tokens
- **WHEN** an LLM call is made with a prompt that has no cache entry and the delegate response includes usage metadata (e.g., 500 input tokens, 100 output tokens)
- **THEN** the LLM API is called, the response is returned to the caller, and a new cache entry is persisted with `input_tokens=500` and `output_tokens=100`

#### Scenario: Second call for same prompt returns cached result
- **WHEN** an LLM call is made with the same prompt and model as a previous call
- **THEN** the cached response is returned without calling the LLM API

### Requirement: Cache response reconstruction with token usage
On cache hit, the system SHALL reconstruct a `ChatResponse` containing the cached text content as the assistant message and a `DefaultUsage` populated from the cached `input_tokens` and `output_tokens` values. If cached token values are null (e.g., entries created before token tracking), the usage SHALL default to 0 for both input and output tokens.

#### Scenario: Cached response includes token usage
- **WHEN** a cached response is returned for a cache entry with `input_tokens=300` and `output_tokens=75`
- **THEN** the reconstructed `ChatResponse` has usage metadata with `promptTokens=300` and `completionTokens=75`

#### Scenario: Cached response with null tokens defaults to zero
- **WHEN** a cached response is returned for a cache entry with null `input_tokens` and null `output_tokens`
- **THEN** the reconstructed `ChatResponse` has usage metadata with `promptTokens=0` and `completionTokens=0`

#### Scenario: Cached response usable with content() extraction
- **WHEN** a cached response is returned
- **THEN** calling `.content()` on the ChatClient response returns the cached text

#### Scenario: Cached response usable with entity() extraction
- **WHEN** a cached response containing valid JSON is returned
- **THEN** calling `.entity(RelevanceResult::class.java)` on the ChatClient response successfully deserializes the cached text

### Requirement: Optional cache cleanup
The system SHALL support an optional configuration property `app.llm-cache.max-age-days` (default: disabled/unset). When set, a scheduled task SHALL delete cache entries with `created_at` older than the configured number of days.

#### Scenario: Cleanup disabled by default
- **WHEN** `app.llm-cache.max-age-days` is not set
- **THEN** no cache entries are deleted automatically

#### Scenario: Cleanup removes old entries
- **WHEN** `app.llm-cache.max-age-days` is set to 30
- **AND** cache entries older than 30 days exist
- **THEN** those entries are deleted by the scheduled cleanup task

#### Scenario: Recent entries are not removed
- **WHEN** `app.llm-cache.max-age-days` is set to 30
- **AND** a cache entry is 15 days old
- **THEN** that entry is NOT deleted by the cleanup task

### Requirement: Cache correctly handles tool-augmented compose calls

`CachingChatModel` SHALL cache the final assistant `ChatResponse` (after any Spring AI tool-call loop has resolved) keyed on `(model, user-prompt-hash)`. Intermediate tool-call requests and responses MUST NOT be persisted as independent cache entries.

A second invocation with the same `(model, user-prompt)` MUST return a cache hit without issuing any new outbound tool invocations.

#### Scenario: Cache hit replays without tool invocations

- **WHEN** the compose stage runs twice with the identical model and user prompt with `searchPastEpisodes` registered
- **THEN** the second run returns the cached `ChatResponse` and records zero `searchPastEpisodes` invocations

