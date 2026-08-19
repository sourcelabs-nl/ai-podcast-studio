## MODIFIED Requirements

### Requirement: Cache miss stores response and token usage
The system SHALL store the LLM response text and token usage in the cache after a cache miss. The `created_at` field SHALL be set to the current UTC timestamp in ISO-8601 format. The `input_tokens` and `output_tokens` fields SHALL be populated from the delegate response's usage metadata (`promptTokens` and `completionTokens`). If usage metadata is not available, token fields SHALL be null. Subsequent calls with the same prompt hash and model SHALL return the cached response with cached token counts.

The cache write SHALL NOT be able to fail the LLM call that produced the response. Concurrent calls with byte-identical prompts (a routine occurrence when a fan-out stage scores duplicate or syndicated articles) can both miss the cache and both insert the same `(prompt_hash, model)` key. The losing insert violates the UNIQUE constraint, but the row it intended to write is already present, so the write is merely redundant. The system SHALL treat a unique-constraint violation on the cache write as success and return the response to the caller.

Any other database failure on the cache write SHALL propagate rather than be swallowed, so a genuine fault is not hidden. Because SQLite's exception translator surfaces constraint failures as `UncategorizedSQLException` rather than `DataIntegrityViolationException`, the system SHALL identify a constraint violation from the SQLite error code (19, `SQLITE_CONSTRAINT`) on the most specific `SQLException` cause.

#### Scenario: First call for a prompt stores result with tokens
- **WHEN** an LLM call is made with a prompt that has no cache entry and the delegate response includes usage metadata (e.g., 500 input tokens, 100 output tokens)
- **THEN** the LLM API is called, the response is returned to the caller, and a new cache entry is persisted with `input_tokens=500` and `output_tokens=100`

#### Scenario: Second call for same prompt returns cached result
- **WHEN** an LLM call is made with the same prompt and model as a previous call
- **THEN** the cached response is returned without calling the LLM API

#### Scenario: Losing a concurrent insert race still returns the response
- **WHEN** the cache write after a miss fails with a SQLite constraint violation (error code 19) because a concurrent call already inserted the same `(prompt_hash, model)` key
- **THEN** the exception is not propagated and the LLM response is returned to the caller

#### Scenario: An unrelated database failure is not swallowed
- **WHEN** the cache write after a miss fails with a SQLException whose error code is not 19 (for example a full disk)
- **THEN** the exception propagates to the caller
