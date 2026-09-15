## Context

Three read endpoints already exist on the episode, all owned-episode guarded, all returning stored data:

| Endpoint | Source | Shape |
| --- | --- | --- |
| `GET .../episodes/{id}/scores` | `EpisodeScore` rows | one row per `(scorerVersion, judgeModel)`, with `overall`, the three component scores, their counts, and `anchorsJson` |
| `GET .../episodes/{id}/metrics` | `ScriptMetricsService` | `ScriptMetricsResult`, computed from the script on request |
| `GET .../episodes/{id}/evaluation-runs` | `EvaluationRun` rows | composition conditions, written only for cache-bypassing runs |

The frontend reaches all three through the existing `/api/**` rewrite. None of them streams, so no route handler is needed.

## Goals / Non-Goals

**Goals:** make the stored evaluation of an episode readable while reviewing that episode, and make every position it reports reachable in one click.

**Non-Goals:** triggering a scoring run from the dashboard (`POST /scores` exists and stays an ops action); comparing episodes against each other, which belongs to the podcast-level `/metrics` and `/evaluation-runs` endpoints and a different page; changing what is judged or measured.

## Decisions

### Turn indices are the spine of the tab

`ScriptJudge` numbers turns with `DialogueScriptParser.parse(script).mapIndexed`, and `ScriptMetrics` numbers `BackchannelCandidate.turnIndex` the same way. Both are 0-based over the same parse. `parseMultiSpeakerScript` in `script-viewer.tsx` is documented as mirroring `DialogueScriptParser`, so its block index is the same number, which is what makes the cross-link possible without any backend work.

That equivalence is an invariant across a language boundary, held by two parsers that have to keep agreeing. It is not enforced anywhere and it is the thing most likely to break this feature quietly: a divergence sends the reviewer to the wrong turn, which is worse than sending them nowhere. Two consequences:

- A comment in each parser naming the other, so a future edit to either one is made knowing what depends on it.
- The tab renders a turn index from the API only when the parsed script actually has a block at that index, and otherwise shows the entry without a jump control. Out-of-range anchors are already dropped server-side by `ScriptJudge.withinRange`, so this is defence against drift, not against the judge.

`ScriptContent` is also used by `ScriptViewer` (the dialog on the episode list) and by the upcoming-episode page. The focused-turn prop is optional and those call sites pass nothing.

### The judge's own caveats are shown, not flattened

`EpisodeScore` carries `scorerVersion` and `judgeModel` because rows from different scorers describe different quantities and must not be averaged. The tab therefore reports scores per row rather than reducing them to one number, and labels each with its judge and version. When several rows exist, the newest is shown expanded and the others are listed beneath it.

`AttentionScore` documents that the equal weighting of the three components is a placeholder and that the components are what to read when deciding what to change. The tab follows that: the components and their counts get the space, `overall` is a single line.

`laughTagsByRole` is documented as a proxy that cannot see a joke without a laugh tag, and `backchannelCandidates` is a shape match, not a verdict. Both are labelled as such in the UI, so a reviewer does not read "1 laugh tag" as "1 joke", or treat every candidate as a defect. A backchannel is a wanted device; the tab shows the candidates and lets the reviewer judge, which is exactly how the "Let's." turn gets caught without condemning "Hm, okay."

### Cross-tab focus lives in the page, not in a URL parameter

The focused turn is ordinary React state in `EpisodeDetailPage`, passed down to `ScriptContent`. Clicking an index sets it and calls the existing `setTab("script")`.

A `?turn=` query parameter was considered and rejected: `useTabParam` writes with `router.replace`, so a second synced parameter means two components racing on the same URL, and a bookmarked link to a highlighted turn is not something anyone asked for. The tab itself stays bookmarkable through the existing hook.

Scrolling uses `scrollIntoView({ block: "center" })` on the focused turn's element, with the highlight cleared on the next tab switch so a stale highlight does not survive.

### Empty states are three different states

Scoring is best-effort: `PodcastService` logs and moves on when the judge fails, so an episode can legitimately have no score. `EvaluationRun` rows are written only for cache-bypassing runs, so most episodes have none by design. Metrics are computed on request and exist whenever a script does. Each section says which of these it is rather than rendering an empty table, because "not scored" and "no ablation ran" mean different things to the reviewer.

## Risks / Trade-offs

- **Parser drift** (above): mitigated by the range check and the paired comments, not eliminated.
- **A score invites being treated as a target.** `overall` is one mean of three placeholder-weighted components over one judge model. Presenting it prominently risks the number becoming the goal. Mitigated by giving the components and their anchors the space and keeping `overall` to one line, in keeping with what `AttentionScore` already documents.
- **A fifth tab on the episode page.** The tab bar is still short at phone width; no other page structure changes.

## Migration Plan

None. Additive, read-only, no persisted state, no backend change. Older episodes without scores or runs render their empty states.

## Open Questions

None blocking.
