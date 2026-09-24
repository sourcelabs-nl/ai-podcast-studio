## Context

See proposal.md for why. Today `CachingChatModel` times each delegate call and hands an
`LlmCallRecord` to `LlmCallLogService.record`, which inserts one `llm_calls` row in its own
transaction and swallows every failure. Served provider and reasoning tokens are already read from
the response metadata (`ServedProvider.kt`, `TokenUsage`). Compose is a blocking `.call()`.

OpenRouter credentials are per user (`UserProviderConfigService`) and are resolved by
`ChatClientFactory` when it builds the chat model; neither `ResolvedModel` nor `llm_calls` holds a key.

A live probe (2026-09-24) of `GET /api/v1/generation?id=gen-…` on a `deepseek/deepseek-v4.1-flash`
request returned 404 immediately after the call and the full stats 10-20 s later, with fields
`latency`, `generation_time`, `native_tokens_completion`, `native_tokens_reasoning`,
`finish_reason`, `provider_name` and `provider_responses[]` (`provider_name`, `status`, `latency`).

A live compose request (experiment episode 276) fixed what the timings mean: `generation_time`
(159,954 ms) spans the whole 160,582 ms request, `latency` (130,538 ms) is the time until the first
answer token after about 10,600 reasoning tokens, and the served attempt's `latency` (1,777 ms) is
the time until the endpoint started responding. None of them is a plain time to first token.

## Goals / Non-Goals

**Goals:**
- Per-request server-side timing breakdown for every OpenRouter request, stored on its `llm_calls` row.
- Compose breakdown per experiment run and variant mean.
- Zero effect on the call being measured: no added latency, no failure propagation.

**Non-Goals:**
- Streaming compose or client-side time-to-first-token measurement.
- Backfilling requests made before this change (their generation id was never stored).
- Stats for the direct `openai` provider, which has no equivalent endpoint.
- Surviving an application restart: lookups pending at shutdown are dropped.

## Decisions

**Generation id from response metadata.** Spring AI copies the completion's `id` into
`ChatResponseMetadata.id`; for OpenRouter that is the `gen-…` id. It is stored on the row at insert
time as `generation_id`. Alternative (reading the raw HTTP body in an interceptor) adds plumbing for
a value already available.

**`record` returns the inserted row id.** The lookup updates the row it belongs to, so
`LlmCallLogService.record` returns the saved id (null when the write failed, in which case no lookup
is scheduled). A separate update method writes the stats in its own transaction and, like `record`,
logs and swallows failures.

**In-memory asynchronous lookup, carrying the key with it.** `ChatClientFactory` passes an
optional `GenerationStatsLookup` (OpenRouter base URL + API key of the user whose call it is) into
`CachingChatModel`; it is null for non-OpenRouter providers. After a successful non-cached call,
`CachingChatModel` hands `(rowId, generationId, lookup)` to a new `GenerationStatsService`, which
launches a coroutine on its own supervisor scope (the project's pattern for background work) that
waits and retries: first attempt after 5 s, then every 10 s, giving up after 3 minutes with a
warning. Alternative considered: a `@Scheduled` sweeper selecting rows that have a generation id and
no stats. It survives restarts, but it would need the user's API key at sweep time, which means
either persisting keys next to telemetry or re-deriving the user from episode/article attribution
(scoring rows have no episode, preview rows have neither). Losing a few lookups on restart is the
cheaper trade for telemetry.

**Storage: columns on `llm_calls`, attempts as JSON text.** New nullable columns `generation_id`,
`first_content_ms`, `generation_time_ms`, `native_completion_tokens`, `native_reasoning_tokens`,
`finish_reason`, `provider_attempts_json`. Retention comes for free from `LlmCallLogCleanup`.
Alternative: a child table for attempts. Attempts are only ever read whole alongside their row and
there are usually one or two, so a JSON column keeps the read a single-table query.
`native_*` tokens are kept separate from the existing `output_tokens`/`reasoning_tokens` because
they are the provider's native counts, which differ from the normalised counts (the probe showed
117 native vs 153 normalised completion tokens) and are what tokens per second must be computed from.

**HTTP client.** The lookup uses a Spring `RestClient` built from the shared `JsonMapper`, parsing
into a small response data class. A 404 means "not yet"; any other non-2xx or I/O error also retries
until the deadline.

**Phases derived in the backend.** `GenerationStats.phases()` splits a request into startup (sum
of attempt latencies, so a fallback's failed attempts count), reasoning (`latency` minus startup) and
writing (`generation_time` minus `latency`). The per-episode response carries the phases, so the
dashboard renders them without repeating the arithmetic.

**Experiment breakdown computed on read.** `ExperimentService.metricsOf` already loads a run's compose
`llm_calls` rows; the breakdown is derived from those rows in a separate `ComposeBreakdown` data
class (startup, reasoning and writing summed over compose requests, reasoning share and tokens per
second from summed native tokens, attempts beyond one per request summed).
It is attached to `ExperimentRunMetrics` and averaged into `ExperimentVariantMeans`. Nothing new
is persisted for experiments, so a breakdown is only readable for 30 days, the same as
`composeDurationMs` already is.

**Frontend.** `latency-tab.tsx` already lists an episode's requests; it gains the breakdown columns
and an expandable attempts line when attempts > 1. No experiments view exists in the dashboard, so
the experiment breakdown is API-only.

## Risks / Trade-offs

- [Generation endpoint changes shape or is rate limited] → lookup failures only cost a measurement;
  parsing ignores unknown fields and every field is nullable.
- [Many concurrent scoring requests each spawn a lookup] → lookups are cheap GETs spread over
  minutes; a bounded parallelism dispatcher (e.g. 4) on the service scope caps concurrency.
- [Stats arrive after the experiment comparison is first read] → the comparison is computed on read,
  so a later read shows them; values are null, never zero, until then.
- [Restart drops pending lookups] → accepted; affected rows simply keep empty stats.

## Migration Plan

Two additive Flyway migrations: `V84__add_llm_call_generation_stats.sql` adds nullable columns, and
`V85__rename_llm_call_ttft_to_first_content.sql` renames `ttft_ms` to `first_content_ms` for what
the live data showed it holds. Existing rows keep nulls. Rollback: the columns are unused by older code.
