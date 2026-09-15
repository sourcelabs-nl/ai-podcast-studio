## Why

Every generated episode is already judged and measured, and none of it is visible in the dashboard. Scoring runs automatically after generation (`PodcastService` calls `EpisodeScoringService.scoreEpisode`), the judge stores an attention score with the turn indices it was derived from, and `ScriptMetricsService` computes turn balance, turn length against the compose prompt's sentence cap, laugh tags, backchannel candidates and same-speaker runs. Reading any of it today means calling the API by hand.

The cost of that is concrete. Episode 219's introduction ended with the interviewer saying "Let's get into it." and the expert's whole next turn being "Let's.", an echo that spends a speaker switch on nothing. The metrics endpoint had already flagged it as `{"turnIndex": 3, "role": "expert", "words": 1, "text": "Let's."}`; it was found by listening instead, because nothing surfaced it. The evaluation data is only useful if reviewing an episode shows it.

The judge was deliberately built to return positions rather than ratings, so that any figure can be checked by opening the script at the named turn. That property is what makes a dashboard view worth building rather than a score readout: the tab can put the reviewer on the turn in question instead of telling them an episode scored 0.94.

## What Changes

- Add an **Evaluation** tab to the episode detail page, beside Script, Articles, Publications and Costs, reading the three existing per-episode endpoints (`/scores`, `/metrics`, `/evaluation-runs`). No backend change.
- The tab reports the attention score and its cliffhanger, humor and teaser components with the counts behind each; the script shape (turns, words and word share per role, median and max turn length, turns over the sentence cap, laugh tags); the anchors and outliers the judge and the metrics found (promises with their payoff turn or none, humor beats, backchannel candidates, same-speaker runs); and the run conditions that produced the script (prompt hash, variety selection, compose model, temperature, cache bypass, tools fired).
- Every turn index in the tab is a control that switches to the Script tab and scrolls to that turn, highlighted. This requires `ScriptContent` to accept a turn to focus and to address its rendered turns.
- The tab is bookmarkable as `?tab=evaluation`, like the existing tabs.

## Capabilities

### Modified Capabilities

- `frontend-dashboard`: a new Evaluation tab on the episode detail page, script turns addressable and focusable from outside the Script tab, and the bookmarkable-tab requirement extended to the new tab.

## Impact

- `frontend/src/components/evaluation-tab.tsx` (new)
- `frontend/src/components/script-viewer.tsx` (turn ids and a focused-turn prop on `ScriptContent`)
- `frontend/src/app/podcasts/[podcastId]/episodes/[episodeId]/page.tsx` (fifth tab, cross-tab focus state)
- `frontend/src/lib/types.ts` (response types for the three endpoints)
- No backend, API, schema or data changes. Read-only: the tab never triggers scoring.
