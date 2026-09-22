## Why

Compose registers `searchPastEpisodes` always and `webSearch` when research is on, and any tool call forces a second compose round that re-sends the whole conversation and reasons over it again. On episode 227 the first round took 126s, the tools about 1s in total, and the second round 1045s; between 67% and 90% of compose output tokens are reasoning. The lookups the model makes are predictable from the topic clusters, so they can run before compose and compose can become a single call.

## What Changes

- New pre-compose research stage, run inside every compose path before the composer is called:
  - **Plan**: one cheap call on the podcast's filter model turns the topic cluster titles (and the focus text of a focus episode) into at most N web search queries (N = 3 for a podcast with deep-dive research, 5 for a focus episode). Recorded in `llm_calls` under its own stage `research-plan`, attributed to the episode. A plan that cannot be parsed falls back to the subjects themselves as queries.
  - **Web search**: the queries run through the existing cached Tavily search, in parallel with bounded concurrency; a failing search yields no results. Every result is recorded against the episode in `episode_research_sources`, for regular and focus episodes alike. Research calls and research cost are accounted as before.
  - **History**: the podcast's past-episode full-text search runs deterministically for the same subjects, capped like the former tool, keeping the focus-episode marker on each match.
- Compose prompts gain a "Background research" block (title, URL, snippet per result) and a "Previously covered" block (past-episode matches). **BREAKING** for prompt behavior: compose registers no tools, so it is always a single call; the prompt text that told the model to call `searchPastEpisodes` or `webSearch` is replaced by instructions on how to use the two blocks, keeping the existing semantics (follow-up headers stay authoritative, focus episodes are continuations, research is attributed).
- A regular episode without deep-dive research runs no web search but still gets the history block.
- A focus episode's feedback recompose reruns the research stage; the plan call and Tavily searches are cache replays, so the research is the same at no cost.
- `llm_calls` records the provider OpenRouter served the request from and the reasoning token count, exposed in `GET /llm/calls/episodes/{id}` (migration V80).
- Compose-stage token totals include every attempt the speaker-tag validation re-issued, not only the last.
- The speaker-tag validation strips the topic-order block before cleaning up the script, removing the false "Discarded ... untagged text after the script: |||TOPIC_ORDER|||" warning.
- Removed: the `searchPastEpisodes` and `webSearch` compose tools and the per-call tool budget.

## Capabilities

### New Capabilities
- `precompose-research`: the research stage that plans queries, runs web searches and past-episode lookups before compose, and hands their results to the compose prompt.

### Modified Capabilities
- `deep-dive-research`: research no longer runs as a compose tool; the budget becomes a cap on planned queries and sources are recorded for every episode.
- `episode-history-lookback`: past-episode lookup runs before compose instead of as a tool; the compose prompt describes the history block instead of the tool.
- `llm-call-telemetry`: each request records the served provider and reasoning tokens, and the research-plan stage is reported.
- `compose-script-cleanup`: validation ignores the topic-order block.
- `cost-tracking`: compose totals cover every attempt; the research-plan call is part of the episode's research cost.

## Impact

- Code: `LlmPipeline`, the three composers, `ComposerUtils`, `ComposeContext`, `ChatClientFactory`, `CachingChatModel`, `TokenUsage`, `RoleTagValidationAdvisor`, `LlmCallLogService`, `LlmCallLatencyService`, `ReportedStage`, `EvaluationRunProvenance`, `PodcastService`, cost mapping; new `research` stage service and plan client; `HistoryLookupTool`, `ResearchTool` and `ToolBudget` removed.
- Database: V80 adds `served_provider` and `reasoning_tokens` to `llm_calls`.
- API: `GET /llm/calls/episodes/{id}` requests gain `servedProvider` and `reasoningTokens`; the latency report gains a `research-plan` stage.
- Cost: one extra small filter-model call per episode, against a compose second round that cost the most of any stage.
