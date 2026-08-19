## Context

`CachingChatModel` decorates the delegate `ChatModel`: on a miss it calls the model, then persists the response under `(prompt_hash, model)`. The cache is a SQLite table with a UNIQUE constraint on that pair, and there is no locking or pre-insert coordination between concurrent callers. Article scoring runs its calls in parallel, and identical prompts inside one run are ordinary rather than exceptional, because duplicate and syndicated articles produce identical text.

The design question is narrow: what should happen to the LLM response when the cache write loses a race it was always allowed to lose.

## Goals / Non-Goals

**Goals:**

- A lost insert race never fails the LLM call that produced the response.
- A genuine database failure on the cache write still surfaces.

**Non-Goals:**

- Preventing the duplicate model call. Both callers have already paid for their response by the time the write happens, and de-duplicating in-flight prompts would mean an in-memory registry of pending keys, which is a much larger change for a saving that only appears when two identical prompts happen to overlap.
- Changing the schema. The UNIQUE constraint is what makes the race benign in the first place: the losing writer's row is already present, written by the winner.

## Decisions

**Catch and continue, rather than check-then-insert.**

A `SELECT` before the `INSERT` would not close the race, only narrow it, since the winner can still commit between the two statements. Catching the constraint violation handles the race at the point where the database has actually adjudicated it. The cache lookup at the start of `call()` already does the useful, non-racy version of that check.

**Detect by SQLite error code, not by Spring exception type.**

The natural catch would be `DataIntegrityViolationException`, but SQLite's exception translator leaves constraint failures uncategorized and throws `UncategorizedSQLException`. The code therefore unwraps with `NestedExceptionUtils.getMostSpecificCause` and tests `SQLException.errorCode == 19` (`SQLITE_CONSTRAINT`). Code 19 covers every constraint kind, which is precise enough here because `UNIQUE (prompt_hash, model)` is the only constraint an `llm_cache` insert can break.

**Narrow the swallow to that one condition.**

`store` rethrows anything that is not a constraint violation. A cache write that fails because the disk is full or the table is missing is a real fault and must not be hidden behind a debug log.

## Risks / Trade-offs

- **Error code 19 is the generic constraint code, so a future NOT NULL or CHECK constraint on `llm_cache` would also be treated as a benign race.** → Accepted while UNIQUE is the only constraint on the table. A future constraint would need this predicate revisited, which the comment on `isUniqueConstraintViolation` records.
- **The duplicate model call is still paid for.** → Out of scope by choice, see Non-Goals. The cost is bounded by how often identical prompts overlap in time.
- **A swallowed violation is only visible at debug level.** → Correct for an expected, benign outcome. The winner's row is in the cache, so the state after the race is exactly the state intended.
