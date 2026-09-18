## Context

See proposal.md - Why. The constraints that shape this design:

- The dashboard has no API client layer: components call `fetch("/api/...")` directly, proxied to the
  backend by the `/api/**` rewrite in `next.config.ts`. `loadJson<T>()` in `evaluation-tab.tsx` is the
  established shape for a GET that must fail softly.
- Response types are hand-written in `frontend/src/lib/types.ts`; there is no codegen.
- `costs-tab.tsx` renders a per-stage table for the episode and receives its data as a prop from the
  page, which fetches it. `evaluation-tab.tsx` is the other shape: a tab that fetches its own data.
- The episode page's tabs are a `TABS` const plus a `useTabParam` hook that syncs the active tab to
  the URL, so a new tab is two additions and is linkable.
- The backend reports `filter`, `dedup`, `compose` and `eval`. The Costs tab's existing rows are
  Scoring, Dedup, Compose and Recap: recap is not a `PipelineStage` (it resolves the filter model),
  and `eval` has no cost row. So the two tables' rows do not correspond one-to-one.

## Goals / Non-Goals

**Goals**

- The percentiles readable where a slow or expensive generation is being examined.
- The window nature of the figures unmissable, not a footnote.

**Non-Goals**

- Per-episode latency. The rows carry no episode attribution, and adding it is a backend change with
  its own design (see `log-llm-call-durations`, Non-Goals).
- A configurable window in the UI. The endpoint takes `days`, but a fixed 7 days answers the question
  and a control invites fiddling with a number nobody has a basis to choose yet.
- Auto-refresh. No polling pattern exists in this codebase, and latency over a 7-day window does not
  move while someone reads it.

## Decisions

**A tab of its own, not a block inside Costs.** The two are different things: cost is this episode's,
latency is a window across all of them. Sharing a tab invites the reader to carry the episode context
from the table above into the table below, which is the one misreading this data allows. A tab also
keeps the stage rows honest, because they do not correspond: Costs has a Recap row that is not a
stage, latency has an `eval` row that has no cost.

**A component that fetches its own data, rather than a prop threaded from the page.** The episode
page's data is episode-scoped; this is not. Fetching inside the tab keeps the window aggregate out of
the episode's data path, and means a failure to load it cannot affect the episode request. This
follows `evaluation-tab.tsx` rather than `costs-tab.tsx`'s prop-driven shape.

**Stage rows are labelled with the backend's stage names**, with `filter` shown as "Scoring" to match
the cost table's existing vocabulary for the same stage. Inventing different names for the same
stages one table apart would be worse than the small inconsistency of `eval` appearing only here.

**The window is stated in the tab's heading**, not in a tooltip or a footnote, because the misreading
it prevents is the whole risk of putting this on an episode page.

**A stage with zero samples renders as an explicit "no requests" row** rather than being dropped.
A dropped row reads as "nothing to worry about here"; the endpoint already returns every stage with
its sample count for this reason.

## Risks / Trade-offs

- **A reader still takes the figures for this episode's timings** → The heading states the window and
  the sample counts are shown. This is mitigated, not eliminated; it is the cost of putting a window
  aggregate on an episode's page at all.
- **A tab holding one small table** → Accepted: the separation is what keeps the window aggregate
  from being read as the episode's own.
- **Percentiles look authoritative on a thin window** → Sample counts are shown per stage, per the
  specs.

## Migration Plan

Additive frontend only. No backend, schema or API change. Rollback is reverting the component, its
tab entry and the types.
