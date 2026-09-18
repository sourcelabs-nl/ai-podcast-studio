## 1. Types and data

- [x] 1.1 Add `StageLatency` and `LlmCallLatencyResponse` interfaces to `frontend/src/lib/types.ts` matching the backend DTOs field-for-field; verify `npx tsc --noEmit` passes
- [x] 1.2 Add the latency block component under `frontend/src/components/` that fetches `/api/llm/calls/latency?days=7` with the `loadJson` pattern from `evaluation-tab.tsx`; verify it compiles and handles the loading, failed and loaded states separately

## 2. Rendering

- [x] 2.1 Render the per-stage table: stage label, samples, p50/p90/p95/p99 and the stage's timeout, numeric columns right-aligned with `tabular-nums` as `costs-tab.tsx` does; verify against a real response from the running backend
- [x] 2.2 Show `filter` as "Scoring" to match the cost table's vocabulary, and render a stage with zero samples as an explicit no-requests row rather than omitting it; verify with a response where one stage has no samples
- [x] 2.3 State the rolling window across all episodes in the block's heading; verify the wording cannot be read as episode-scoped
- [x] 2.4 Format durations readably (ms below a second, seconds or minutes above) so a 20-minute timeout and a 900ms p50 are both legible in one column; verify both extremes render sensibly

## 3. Wiring

- [x] 3.1 Add a Latency tab to the episode detail page's `TABS` and render the component in it; verify the tab is selectable and its URL parameter round-trips
- [x] 3.2 Verify the tab does not delay or break the episode page's own data loading (the page renders before latency arrives)

## 4. Verification

- [x] 4.1 Run `npx tsc --noEmit` in `frontend/` and confirm it passes (never `npm run build` while the dev server is running)
- [x] 4.2 View the Latency tab of a real episode in the running dashboard and confirm the block renders with live data; captured on episode 221. NOT verified: the zero-sample row, because all four stages had traffic in the window
- [x] 4.3 Run `/code-review --all` over the change and fix violations, repeating until clean
