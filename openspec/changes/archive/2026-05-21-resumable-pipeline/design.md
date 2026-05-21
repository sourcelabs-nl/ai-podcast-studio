## Context

The episode generation pipeline runs multiple LLM stages sequentially (aggregate, score, dedup, compose) followed by post-processing (TTS, recap, sources). Currently, all intermediate results are held in memory. If any stage fails, the entire pipeline must be re-run from scratch, producing different output due to LLM non-determinism. The `pipelineStage` field is cleared on failure, so there's no record of where it failed.

Episodes 81-86 (2026-04-01) demonstrated the problem: each discard+regenerate cycle produced completely different scripts from the same 45 articles because the full pipeline ran from scratch each time.

## Goals / Non-Goals

**Goals:**
- Enable retry from the exact failed stage, preserving all previously completed LLM work
- Persist intermediate results (dedup article-topic assignments, script) eagerly between stages
- Auto-detect the correct resume point from persisted state
- Provide real-time SSE feedback for all state transitions
- Maintain backward compatibility with existing flows (approve, discard, regenerate)

**Non-Goals:**
- Making the pipeline itself deterministic (LLM outputs will always vary)
- Adding manual stage selection (the system auto-detects the resume point)
- Persisting dedup filter results as a separate table (reuse existing `episode_articles`)
- Database schema changes (all persistence uses existing columns)

## Decisions

### 1. Split pipeline into independently callable stages

**Decision:** Extract `LlmPipeline.run()` into three stage methods: `aggregateScoreAndFilter()`, `dedup()`, `compose()`. Keep `run()` as a convenience wrapper.

**Why over keeping monolithic:** The retry flow needs to call individual stages. Callbacks or resume-by-offset approaches are fragile. Explicit stage methods are testable and readable.

**Why keep `run()`:** Backward compatibility for `preview()` and the scheduler's `generateBriefing()` path. Also avoids changing every caller.

### 2. Persist after dedup, persist after compose

**Decision:** Save `episode_articles` links (with topics) immediately after dedup completes. Save `scriptText` to the episode immediately after compose completes. Both happen before moving to the next stage.

**Why here specifically:** These are the two most expensive LLM outputs. Scoring already persists per-article. TTS retry already works via the approve button. Recap already has a regenerate endpoint.

**Why not persist dedup to a separate table:** The `episode_articles` table already has `topic` and `topic_order` columns. Saving links there after dedup is natural and requires no schema changes.

### 3. Auto-detect resume point from persisted state

**Decision:** Use a simple waterfall check: (1) has script? resume from POST_COMPOSE. (2) has episode_articles? resume from COMPOSE. (3) otherwise, FULL_PIPELINE.

**Why over explicit stage tracking:** No new columns needed. The presence of data is a reliable indicator of completion. Simpler than maintaining a separate "last completed stage" field that could drift.

### 4. Preserve pipelineStage on failure

**Decision:** Stop clearing `pipelineStage` to null in `failEpisode()` and `cleanupStaleGeneratingEpisodes()`.

**Why:** The field already tracks which stage was running. Keeping it on failure lets the UI show "Failed at: composing" and gives the retry logic additional context (though resume point detection doesn't depend on it).

### 5. Run retry async

**Decision:** The retry endpoint returns 202 immediately and runs the remaining pipeline stages asynchronously (same pattern as `audioGenerationService.generateAudioAsync()`).

**Why:** Pipeline stages can take minutes. The frontend gets real-time updates via SSE events. Synchronous retry would time out for long-running stages.

### 6. SSE events for all intermediate saves

**Decision:** Publish `episode.stage` events with new stage values (`dedup_saved`, `script_saved`, `marking_processed`, `generating_recap`) plus a new `episode.retrying` event.

**Why reuse `episode.stage`:** The frontend already handles this event type for pipeline progress. Adding new stage values requires only toast message additions, no structural frontend changes.

## Risks / Trade-offs

**[Risk] Retry on partially-saved state** - If the app crashes between saving episode_articles and updating the episode's token counts, the episode has links but incomplete cost data.
→ Mitigation: The finalization step recalculates costs from the episode record. Partial token data just means slightly inaccurate cost tracking for that episode, not incorrect behavior.

**[Risk] Concurrent retry and scheduled generation** - If a user retries a FAILED episode while the scheduler also triggers generation for the same podcast.
→ Mitigation: `resetForRetry()` sets status to GENERATING. `hasActiveEpisode()` checks for GENERATING status, so the scheduler will skip that podcast.

**[Risk] Retry after code change alters behavior** - If the compose step is retried after a code fix, the new code runs against old dedup results.
→ Mitigation: This is the desired behavior (preserve dedup, re-run compose with fix). If the user wants fully fresh output, they can use the existing "Regenerate" button instead of "Retry".

**[Trade-off] episode_articles saved before articles are marked as processed** - During the window between saving links and marking articles processed, a concurrent generation could pick up the same articles.
→ Acceptable: The `hasActiveEpisode()` guard prevents concurrent generation for the same podcast.
