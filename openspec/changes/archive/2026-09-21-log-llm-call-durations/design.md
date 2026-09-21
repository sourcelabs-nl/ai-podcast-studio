## Context

See proposal.md - Why for the motivation. The constraints that shape this design:

- `CachingChatModel` wraps every `ChatModel` this application builds, and `ChatClientFactory.buildCachingModel`
  is the only place it is constructed. Spring AI's internal tool execution re-enters the `ChatModel`
  for each round-trip (the class already reasons about this: its cache lookup fires only on the
  initial call, detected by the absence of `ASSISTANT`/`TOOL` messages). So one `call()` equals one
  HTTP request, which is exactly the grain the specs require.
- A cache hit returns `reconstructResponse(cached)` without touching the delegate, so it carries
  tokens and cost but no network time.
- `ResolvedModel` already carries `provider`, `model` and `stage`, and is in scope at the
  construction site.
- The store is SQLite with a single writer. The pipeline runs scoring at concurrency 10, so up to
  ten calls can finish at once.

## Goals / Non-Goals

**Goals**

- Per-request latency at a grain that can justify or shrink the four `app.llm.timeouts` values.
- A percentile read path that does not require touching the database directly.

**Non-Goals**

- Deriving the existing `articles` and `episodes` token/cost aggregates from the new rows. The new
  rows carry tokens and cost, so that becomes possible later, but changing the accounting path is a
  separate change with its own risk.
- Attributing a row to an episode. `CachingChatModel` has no episode in scope, and threading one
  through every call site would be a wide change for a question about latency distribution.
- Micrometer/actuator instrumentation. It would measure the same thing in memory; with a persisted
  log it is redundant.

## Decisions

**Record in `CachingChatModel`, not in the composers.** It is the only chokepoint every request
passes through, and it sees the tool loop's follow-up requests that a composer-level timer cannot
separate. The alternative (timing at the five `TokenUsage.fromChatResponse` call sites) reproduces
the exact defect being fixed: those sites wrap a whole stage, and for scoring a whole batch.

**Give `CachingChatModel` the `ResolvedModel` rather than deriving context from the prompt.** It
currently recovers the model name from `prompt.options?.model` with a `"default"` fallback, which is
not good enough for grouping, and the stage is not in the prompt at all. Both are available at the
construction site, so they are passed in. `cacheEnabled = false` (evaluation runs) still records:
an evaluation's latency is real latency.

**Time the delegate call only.** The timer wraps `delegate.call(prompt)`, so cache lookup and the
cache write stay outside it. A cache hit never reaches the delegate and is recorded with a zero
duration and `cacheHit = true`; the spec requires percentiles to exclude those rows, so the stored
zero is never read as a latency.

**Write the row outside the caller's transaction, and never fail the call.** The write runs in its
own transaction (`REQUIRES_NEW`) and is wrapped so any exception is logged and swallowed, including
the SQLite busy case. A record is written for a failed request too, in a `finally`-style path, so
the original exception propagates unchanged.

**Compute percentiles in SQL over an indexed window.** `duration_ms` percentiles come from an
`ORDER BY duration_ms LIMIT 1 OFFSET n` per percentile over the filtered set rather than by loading
rows into the JVM; the filter is `started_at >= :from AND cache_hit = 0 AND outcome = 'ok'`, backed
by an index on `(started_at)`. Exact rather than approximate, which at a few hundred rows a day is
affordable and avoids a sketch the reader has to trust.

**Retention runs on the existing scheduler structure**, alongside `BriefingGenerationScheduler` and
`SourcePollingScheduler`, deleting rows older than `app.llm.call-log.retention` (a `Duration`, as the
timeouts are). It runs daily and off the generation window, so the delete never contends with a
pipeline run for the single writer.

**Table shape** (`V70__add_llm_call_log.sql`, current head `V69`):

| column | note |
|---|---|
| `id` | follows the project's existing id convention |
| `started_at` | when the request began; the column percentile windows filter on |
| `stage` | `filter` / `dedup` / `compose` / `eval`, the `PipelineStage` value |
| `provider`, `model` | from `ResolvedModel`; latency differs sharply per model |
| `duration_ms` | integer milliseconds |
| `input_tokens`, `output_tokens` | latency tracks output length; needed to read the spread |
| `reported_cost_usd` | nullable; present so the rows can later serve as the cost ledger |
| `cache_hit` | boolean; excluded from percentiles |
| `outcome` | `ok` / `error`; excluded from percentiles when `error` |
| `error_type` | nullable; the exception class, to tell a timeout from a 5xx |

## Risks / Trade-offs

- **The log adds a write to every LLM call, on a single-writer database** → The write is small, out
  of the caller's transaction, and swallows failures, so contention degrades the telemetry rather
  than the pipeline. Scoring's concurrency of 10 is already the pipeline's existing write pattern.
- **Retention deletes the history that a later cost-ledger change would want** → The retention
  window is configuration, and this change does not make anything depend on the rows surviving. A
  change that moves cost accounting onto them must revisit the window first; noted here because the
  two decisions interact.
- **A percentile over few samples invites a wrong timeout decision** → The read path reports the
  sample count per stage, per the specs, so a thin window is visible rather than inferred.
- **A row is written per call for the `eval` stage too, which can run over a large archive** →
  Accepted: those are real requests, and retention bounds the total.

## Migration Plan

Additive only: a new table, no change to existing columns or accounting. Deploy is the migration
plus the restart. Rollback is to revert the code; the table can stay, since nothing reads it.
Percentiles are meaningful only after the first generation runs, so the timeout question is
answered a few days after deployment, not immediately.

## Open Questions

- Whether the eventual compose timeout should be a multiple of the measured p99 or an absolute
  ceiling. Answerable once numbers exist; it changes no part of this design.
