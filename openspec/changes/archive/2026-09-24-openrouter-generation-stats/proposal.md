## Why

A single compose request can take anywhere from 3 seconds to almost 9 minutes (episode 225: one
526 s request), and today that request is a black box: `llm_calls` records its start, end, served
provider and reasoning tokens, but not whether the time went to queueing before the first token, to
reasoning, to slow generation on one endpoint, or to OpenRouter falling back between providers.
OpenRouter already measures all of this server-side and exposes it per request through
`GET /api/v1/generation?id=…`, so the breakdown can be captured without changing how compose calls
the model.

## What Changes

- Every OpenRouter request records the generation id OpenRouter returned for it.
- Shortly after the request, the generation stats are fetched and stored on the same request record:
  time until the first answer token, total generation time, native completion and reasoning tokens,
  finish reason, and every upstream attempt (provider, status, latency). The lookup retries, because the stats appear
  10-20 seconds after the request completes.
- Each request's time is split into startup (until the provider started responding), reasoning
  (until the first answer token) and writing.
- The per-episode request list (`GET /llm/calls/episodes/{id}`) returns those values and phases.
- The experiment comparison reports a compose performance breakdown per run and per variant mean:
  startup, reasoning and writing time, reasoning share, output tokens per second and number of
  fallback attempts.
- The dashboard's per-episode request list shows the breakdown per request.
- Streaming compose is out of scope; it is reconsidered once this data shows whether the server-side
  timings account for slow requests.

## Capabilities

### New Capabilities
- `llm-generation-stats`: fetching OpenRouter's per-request generation stats after the call and
  deriving the compose performance breakdown used by experiments.

### Modified Capabilities
- `llm-call-telemetry`: a request record gains its generation id and the fetched generation stats,
  and the per-episode request list exposes them.
- `frontend-llm-latency`: the per-episode request list shows the per-request breakdown.

## Impact

- Backend: `llm` package (`CachingChatModel`, `LlmCallLogService`, `LlmCallTypes`, new stats
  fetcher), `store` (`LlmCall`, repository), one Flyway migration on `llm_calls`, `ExperimentService`
  and `ExperimentTypes`.
- External API: one extra `GET https://openrouter.ai/api/v1/generation` per OpenRouter request
  (free, authenticated with the OpenRouter key already configured for the model call).
- Frontend: `components/latency-tab.tsx`, `lib/types.ts`.
- Retention is unchanged: the stats live on `llm_calls` rows and expire with them after 30 days.
