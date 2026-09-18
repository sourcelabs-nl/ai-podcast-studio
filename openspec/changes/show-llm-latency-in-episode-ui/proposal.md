## Why

The per-request latency recorded by `llm-call-telemetry` is only readable by calling
`GET /llm/calls/latency` by hand. The question it was built to answer, whether the 20-minute compose
timeout is anywhere near what requests actually take, is one you ask while looking at an episode that
was slow or expensive, and at that moment the dashboard shows cost per stage but nothing about time.

Reading it through curl also means the person most likely to want it, whoever is looking at a
generation that behaved oddly, has to leave the tool they are already in and know an endpoint exists.

## What Changes

The episode detail page gains a Latency tab showing p50/p90/p95/p99 per pipeline stage next to the
timeout that stage is configured with, so a percentile that is creeping toward its ceiling is visible
where a slow generation is being examined.

The block states, in its heading, that it covers a rolling window across all episodes rather than the
episode being viewed. The telemetry records no episode attribution by design, and a per-stage table
sitting on one episode's page would otherwise be read as that episode's timings.

The sample count each percentile rests on is shown, and a stage that issued nothing in the window
reads as having no data rather than as having no problem.

The tab fails quietly: if the request fails, it says so and the rest of the page is unaffected.

## Capabilities

### New Capabilities
- `frontend-llm-latency`: the dashboard's presentation of recorded per-request LLM latency, its
  placement, and how it distinguishes a window aggregate from episode-scoped data.

### Modified Capabilities
<!-- None. `llm-call-telemetry` already requires the percentiles to be readable through the API and
     to report sample counts; this change consumes that contract without altering it. -->

## Impact

- **Frontend**: a new component under `frontend/src/components/`, rendered by a new tab on the episode
  detail page (added to that page's `TABS`); new response types in `frontend/src/lib/types.ts`.
  The call goes to `/api/llm/calls/latency` through the existing `next.config.ts` rewrite, so no
  route handler is added.
- **Backend**: none. The endpoint, its shape and its retention already exist.
- **Not affected**: the Costs tab and the episode's own cost accounting.
