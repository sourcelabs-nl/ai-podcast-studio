## 1. Types and data loading

- [x] 1.1 Add response types for `EpisodeScore`, `ScriptMetricsResult` (with `RoleMetrics`, `TurnLengthMetrics`, `BackchannelCandidate`, `SameSpeakerRun`) and `EvaluationRun` to `frontend/src/lib/types.ts`, matching the Kotlin shapes
- [x] 1.2 Add a parsed type for `anchorsJson` (`promises`, `humorBeats`, `teaserTopics`) and parse it defensively: a row whose anchors do not parse still renders its scores

## 2. Addressable script turns

- [x] 2.1 Give each rendered turn in `MultiSpeakerScript` a stable DOM id derived from its block index
- [x] 2.2 Add an optional `focusedTurn` prop to `ScriptContent`; on change, scroll that turn into view (`block: "center"`) and apply a highlight style
- [x] 2.3 Export the block count (or a parse helper) so a caller can tell whether a given turn index exists
- [x] 2.4 Add the paired comments in `parseMultiSpeakerScript` and `DialogueScriptParser` naming each other and the index equivalence they hold
- [x] 2.5 Confirm the existing `ScriptViewer` dialog and the upcoming-episode page still render unchanged without the new prop (turn numbers gated behind `showTurnNumbers`, set only on the episode detail page)

## 3. Evaluation tab component

- [x] 3.1 Create `frontend/src/components/evaluation-tab.tsx` fetching the three endpoints for the episode
- [x] 3.2 Attention score section: newest row expanded with components and counts, judge model and scorer version labelled, older rows listed separately and never averaged
- [x] 3.3 Script shape section: turn and word totals, per-role turns, words, word share, median and max turn length, turns over the sentence cap, laugh tags labelled as a proxy
- [x] 3.4 Anchors and outliers section: promises (promise turn, payoff turn or unpaid), humor beats (role, reacts-to-previous), backchannel candidates (labelled as shape matches, not defects), same-speaker runs
- [x] 3.5 Run conditions section: prompt hash, variety selection, compose model, temperature, cache bypassed, cache hit, tools fired
- [x] 3.6 Three distinct empty states: not scored, no evaluation runs, no script to measure
- [x] 3.7 Render each turn index as a jump control, omitted when the parsed script has no turn at that index

## 4. Page wiring

- [x] 4.1 Add `"evaluation"` to `TABS` and a `TabsTrigger`/`TabsContent` pair in the episode detail page
- [x] 4.2 Hold focused-turn state in the page, pass it to `ScriptContent`, and clear it on tab switch
- [x] 4.3 Wire the tab's jump callback to set the focused turn and call `setTab("script")`

## 5. Verify

- [x] 5.1 `npx tsc --noEmit` in `frontend/` (never `npm run build` while the dev server is running)
- [x] 5.2 Open episode 219's Evaluation tab: confirm the 0.94 score with its components, and the `"Let's."` backchannel candidate at turn 3
- [x] 5.3 Click that candidate and confirm the Script tab scrolls to and highlights the expert's `"Let's."` turn, verifying the index equivalence end to end
- [x] 5.4 Check an episode with no score and one with no evaluation runs for their empty states
- [x] 5.5 Confirm `?tab=evaluation` selects the tab on load
- [x] 5.6 Check the tab at phone width
- [x] 5.7 Run `/code-review` on the changed frontend files and fix what it finds
