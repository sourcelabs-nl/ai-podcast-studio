## Why

The compose stage has a 20-minute request timeout (`app.llm.timeouts.compose`), and nothing we
record justifies that number. The comment defending it cites an 18m11s compose run, but that is
wall-clock over the whole stage: the timeout is okhttp's `callTimeout` on the OpenAI SDK client
(`OpenAiClientSupport.kt`), which applies per HTTP request. With Spring AI's internal tool
execution a single compose invocation issues several requests, each getting its own full
allowance, and the local tool work between them falls outside the timeout entirely. So the
measurement we have and the ceiling we set are not the same quantity, and the ceiling could be
far too generous without anyone noticing.

Nothing in the system can settle this today. No table carries a duration: `TokenUsage` holds only
tokens and cost, and the migrations add token and cost columns only. The `measureTimedValue` logs
in the composers wrap a whole stage (and for scoring, a loop over every article), so they
overstate single-request latency by exactly the amount in question. The `llm_cache` table is a
cache keyed on `(prompt_hash, model)`, not a call log: an identical second call is a hit and
writes no row.

Without per-request latency we cannot size any of the four stage timeouts on evidence, tell a
genuinely slow model from a hung one, or see latency regress when a stage's model changes.

## What Changes

Every LLM HTTP round-trip is recorded as one row: which stage and model it belonged to, how long
it took, how many tokens it moved, whether it was served from cache, and whether it succeeded.
Recording happens where the calls actually go out, so the tool-execution loop produces one row per
round-trip rather than one row per stage.

Cache hits are recorded but marked, because they perform no HTTP work; counting them as fast
calls would drag the percentiles down and hide the latency of real requests.

The recorded latency is readable as percentiles (p50/p90/p95/p99) per stage over a time window,
via the API rather than by querying the database.

Because the scoring stage alone issues on the order of one call per article per episode, the log
is bounded by a retention window rather than kept forever.

Logging never affects the call it measures: a failure to record is logged and swallowed, and a
recorded failure is distinguishable from a success so that timeouts and upstream errors cannot
quietly flatter the percentiles.

## Capabilities

### New Capabilities
- `llm-call-telemetry`: per-HTTP-call latency and outcome recording for LLM requests, its
  retention, and the percentile query over it.

### Modified Capabilities
<!-- None. `cost-tracking` describes aggregates on articles and episodes; this change records at a
     different grain (one row per round-trip) and leaves those aggregates and their requirements
     untouched. The new rows carry tokens and cost as well, so a later change could derive the
     aggregates from them, but that refactor is not part of this one. -->

## Impact

- **Schema**: new `llm_calls` table (migration `V70`, current head is `V69`).
- **Code**: `CachingChatModel` (the single chokepoint every `ChatModel.call()` passes through) gains
  the timing and the write; `ChatClientFactory.buildCachingModel` supplies it the `ResolvedModel`
  and the new repository. New entity, repository, service and controller for the percentile query.
  Retention cleanup joins the existing scheduler structure.
- **Config**: retention window in `application.yaml`.
- **Not affected**: the composers, `LlmPipeline` and `EpisodeService` keep their existing token and
  cost accounting unchanged; their `measureTimedValue` stage logs stay as they are.
- **Volume**: roughly one row per article scored plus a handful per episode for the other stages,
  on the order of a few hundred rows per episode per day.
