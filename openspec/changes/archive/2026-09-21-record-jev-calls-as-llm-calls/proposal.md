## Why

The dedup stage's already-covered gate runs on Jev, and nothing in the UI can
see it. `LlmCall` rows are written only by `CachingChatModel`, and `JevClient`
issues its request through a plain `RestClient` outside Spring AI, so the gate
leaves no row: it is absent from the latency percentiles, absent from an
episode's request list, and a failed call leaves no trace but a WARN in
`app.log`.

That gap is already costing information. On 2026-09-21 the gate cut the dedup
stage from 36.1s to 11.8s on one run and returned nothing on two others during
an OpenRouter incident, and none of that is visible anywhere a person looks.
Its cost is the one part that does arrive, folded into the dedup stage total,
where it cannot be told apart from the clustering call it relieves.

A second Jev caller is planned, checking finished scripts, and it is intended to
hold an episode back from publishing. Blocking on a signal whose latency and
failure rate cannot be read would be blind, so the telemetry comes first.

## What Changes

`JevClient` records one `LlmCall` row per HTTP attempt, carrying the duration,
the input tokens, the cost Jev reports, and the outcome, with the failing
status and `error_type` on a failure. Per attempt rather than per `ask`, so a
retried 529 is visible as two failures rather than disappearing into one
successful call, and so the wait between attempts is never counted as latency.

The row names the episode the request was issued for. `CoveredTopicGate` does
not currently receive an episode id, so `TopicDedupFilter` passes it in.

Latency reporting stops being defined by `PipelineStage`. Today
`LlmCallLatencyService` reports one entry per enum constant, so a row from a
stage outside that enum is stored and then never read back. The gate is not a
pipeline stage: it has no entry in the stage defaults and its model and timeout
come from `app.llm.dedup.gate`. Reported stages become their own list, holding
the pipeline stages plus the gate with the timeout it is actually issued with.

The gate's own behaviour does not change. Nothing here alters what it excludes,
what it costs, or what happens when it answers nothing, and no publish gate is
introduced by this change.

## Capabilities

### New Capabilities

None. This change makes an existing call observable.

### Modified Capabilities

- `llm-call-telemetry`: a request issued outside Spring AI is recorded too, one
  row per attempt, and the set of stages reported back is no longer the
  `PipelineStage` enum.
- `jev-decisions`: a Jev call is recorded as an LLM request, and the caller
  supplies the episode it belongs to.

Note: `llm-call-telemetry` has no spec under `openspec/specs/` yet. It is
introduced by `log-llm-call-durations` and extended by
`attribute-llm-calls-to-episode`, both implemented and awaiting archive, so the
delta here is written against those.

## Impact

- `JevClient`: gains the call log, and an episode id and stage on its request.
- `CoveredTopicGate`, `TopicDedupFilter`: thread the episode id through.
- `LlmCallLatencyService`, `PipelineStage`: reported stages are decoupled from
  the pipeline enum.
- No database migration: `llm_call` already holds every column needed.
- No frontend change. The latency tab renders whatever stages the API returns,
  so the gate appears once the API reports it.
