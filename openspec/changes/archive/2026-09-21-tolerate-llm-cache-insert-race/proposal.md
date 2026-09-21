## Why

`CachingChatModel` writes a cache entry after every cache miss, and `llm_cache` has a UNIQUE constraint on `(prompt_hash, model)`. Stages that fan out issue several LLM calls concurrently, and article scoring routinely sends byte-identical prompts within one run because duplicate and syndicated articles produce the same text. Two such callers both miss the cache, both call the model, and both insert the same key. The loser's insert violates the constraint.

That exception propagates out of `call()`, so a redundant write, one whose only effect would have been to store a row that is already there, fails the LLM call that produced it. The response was already computed and paid for.

## What Changes

- `CachingChatModel` routes the post-miss write through a private `store(entry)` that catches a unique-constraint violation and treats it as success, logging at debug that a concurrent call got there first. The response is returned to the caller either way.
- Any other database failure on the cache write SHALL still propagate, so a real problem (a full disk, a missing table) is not silently swallowed.
- Constraint detection inspects the SQLite error code rather than the Spring exception type, because SQLite's exception translator surfaces constraint failures as `UncategorizedSQLException` rather than `DataIntegrityViolationException`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `llm-cache`: the cache-miss write requirement gains the behaviour for a lost insert race.

## Impact

- Backend: `CachingChatModel` (new private `store` and `isUniqueConstraintViolation` helpers, new `SQLITE_CONSTRAINT_ERROR_CODE` constant).
- Tests: `CachingChatModelTest` gains coverage for the lost race and for an unrelated database failure still propagating.
- No schema change: the UNIQUE constraint stays exactly as it is, and remains the mechanism that keeps the cache free of duplicate keys.
- No API, frontend, or configuration change.
