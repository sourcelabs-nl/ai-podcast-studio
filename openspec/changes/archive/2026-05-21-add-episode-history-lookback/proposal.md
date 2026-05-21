## Why

The current `TopicDedupFilter` only compares candidate articles against article titles + summaries from the last `recapLookbackEpisodes` (default 7) episodes; the stored `episodes.recap` and `episodes.script_text` are never read back into any pipeline stage. The compose LLM has no way to know that a topic like "speckit" was covered weeks ago and re-introduces it as brand new. This change closes that gap by indexing prior episode content in SQLite FTS5 and exposing a Spring AI tool the compose model can call to check before treating any topic as new. It also lands the first piece of tool-calling infrastructure in the codebase, which a separate deep-dive-research change will reuse.

## What Changes

- Add a SQLite FTS5 virtual table `episode_history_fts` over `episodes.recap`, `episodes.script_text`, and the joined `episode_articles.topic` list. Triggers keep it in sync; one-time backfill seeds existing `GENERATED` episodes.
- Introduce Spring AI tool-calling in this codebase. Extend `ChatClientFactory` with a compose-specific path that registers tools. Filter and score stages remain tool-less.
- Add a `ToolBudget` request-scoped wrapper that caps per-episode tool invocations and short-circuits with a structured "budget exhausted" response (no exceptions).
- Add `HistoryLookupTool` exposing `searchPastEpisodes(query)` as a Spring AI tool, backed by a new `EpisodeHistoryRepository.search(podcastId, query, limit=5)`. Cap 5 calls per episode. Returns date, topics, and recap snippet (~280 chars); never returns full script text.
- Add a prompt block (in all composers) instructing the LLM to call `searchPastEpisodes` before treating any topic as new.
- Audit `CachingChatModel` for tool-loop compatibility: confirm the second identical compose call is a cache hit with zero tool invocations.

This change is independent of script-variety and deep-dive-research. The deep-dive change will reuse `ChatClientFactory` tool registration and `ToolBudget` by adding its own tool alongside.

## Capabilities

### New Capabilities
- `episode-history-lookback`: Always-on Spring AI tool (`searchPastEpisodes`) backed by a SQLite FTS5 index over prior recaps, scripts, and topic labels, so the compose model can confirm whether a subject was already covered.

### Modified Capabilities
- `llm-processing`: Compose stage registers tools; filter/score remain tool-less.
- `article-dedup-filter`: Unchanged at the filter level; the new tool is purely additive at compose time.
- `database-migrations`: New migration creates the FTS5 virtual table, triggers, and backfill.
- `llm-cache`: `CachingChatModel` verified to cache the final assistant message under Spring AI tool loops; intermediate tool-call deltas not cached as separate entries.

## Impact

- **Code** — `src/main/kotlin/com/aisummarypodcast/llm/{ChatClientFactory,CachingChatModel,BriefingComposer,InterviewComposer,DialogueComposer}.kt`; new `llm/HistoryLookupTool.kt`, `llm/EpisodeHistoryRepository.kt`, `llm/ToolBudget.kt`.
- **Database** — one new Flyway migration creating the FTS5 virtual table, sync triggers, and the one-shot backfill.
- **API** — no changes.
- **Frontend** — no changes.
- **Dependencies** — none (Spring AI tool-calling is already on the classpath; SQLite FTS5 ships with the bundled driver).
- **Cost** — no new external calls; LLM cost may rise slightly due to tool-call round-trips, mitigated by the 5-call per-episode budget.
