## Why

The Latency tab sits on an episode's page and does not describe that episode. It reports
p50/p90/p95/p99 per stage over a rolling 7-day window across every episode, and says so in its
heading because `llm-call-telemetry` deliberately records no episode attribution. So the tab
answers "how slow are compose requests lately" while the person reading it is looking at episode
#224 and asking "why was this one slow".

Those are different questions, and only the second one has an occasion. Nobody opens an episode to
ask about the fleet; they open it because that generation behaved oddly, and at that moment the
window aggregate actively misleads: a Compose row reading 11 requests with a 16m48s p95 looks like
this episode issued 11 requests and one of them took 16 minutes, when it is 11 requests spread over
every episode of the last week.

The attribution is cheap to add and was left out rather than ruled out. Every stage already runs
with a persisted episode in scope: `PodcastService.runGenerationPipeline` inserts the episode row
before scoring, dedup and compose, and the eval stage runs against a finalized episode. The id is
sitting in the caller's frame at every call site; it simply is not carried down to the row.

## What Changes

Every recorded LLM request carries the episode it was issued for, where one exists. Filter, dedup,
compose and eval calls made during a generation are attributed to the episode being generated, and
the eval stage's calls to the episode being scored.

Requests made outside a generation, such as preview and ad-hoc source scoring, record no episode.
The attribution is optional rather than required, because those paths are real and have no episode
to name, and inventing one would be worse than recording none.

Attribution is carried as an explicit parameter along the existing call path rather than through
ambient context. The composers cross a coroutine dispatcher boundary before reaching the model, so
a thread-local would arrive empty; the codebase also has no ambient-context convention to follow.

The recorded latency becomes readable for one episode: percentiles per stage over that episode's
requests alone, with no time window, since an episode is a bounded set rather than a period.

The individual requests of one episode become readable as well: stage, model, start time, duration,
outcome and whether it was served from cache. At an episode's volume, on the order of a dozen
compose requests, the list is what actually answers the question, and a p99 over eleven samples is
the slowest of the eleven rather than a tail estimate.

The episode page's Latency tab shows the episode's own figures instead of the window aggregate: the
per-stage percentiles, and the request list beneath them.

Requests recorded before this change carry no episode, so they appear in no episode's tab. The tab
distinguishes an episode that issued nothing from an episode generated before attribution existed,
rather than showing both as empty.

## Capabilities

### New Capabilities
- `llm-call-episode-attribution`: which LLM requests are attributed to which episode, how the
  attribution reaches the recorded row, and what happens on the paths that have no episode.

### Modified Capabilities
- `llm-call-telemetry`: the percentile query gains an episode-scoped form alongside the windowed
  one, and the recorded row gains an optional episode. The per-request read over one episode is new
  surface on the same capability.
- `frontend-llm-latency`: the tab's scope changes from a cross-episode window to the episode being
  viewed, which reverses the requirement that it announce itself as not being about this episode,
  and it gains the per-request list.

## Impact

- **Schema**: `llm_calls` gains a nullable `episode_id` and an index supporting the per-episode
  reads (migration `V72`, current head is `V71`). Existing rows keep `NULL`, so every episode
  generated before this change shows nothing.
- **Code**: `LlmCallRecord`, the `LlmCall` entity and `LlmCallLogService` carry the id;
  `CachingChatModel` receives it and writes it; `ChatClientFactory` passes it to the model it
  builds. The id reaches the factory from `ComposeContext` for compose, from a new parameter on
  `ScriptJudge.judge` for eval, and from `LlmPipeline` for filter and dedup, in all cases
  originating at `PodcastService.runGenerationPipeline` or `EpisodeScoringService`.
- **API**: `GET /llm/calls/latency` accepts an episode, and a per-request read over one episode is
  added alongside it.
- **Frontend**: `LatencyTab` is given the episode it is rendered for, and gains the request list.
- **Not affected**: the retention window, the cost accounting on articles and episodes, and the
  existing windowed percentile query, which keeps working unchanged when no episode is named.
