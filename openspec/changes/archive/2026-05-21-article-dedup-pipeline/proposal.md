## Why

Episodes are repeating content from previous episodes because of three compounding issues: (1) discarding an episode unconditionally resets its articles' `isProcessed` flag even when those articles are linked to published episodes, causing them to re-enter the pipeline indefinitely; (2) adding a new source pulls in all historical content rather than only content published after the latest published episode; (3) different sources covering the same story produce different articles (different `content_hash`) that all pass through to the composer, with only a lossy 2-3 sentence recap as an advisory dedup signal.

## What Changes

- Fix the discard reset guard: only reset `isProcessed` on articles that are not linked to any GENERATED episode with publications
- Add an article age gate: only include articles published/ingested after the latest GENERATED+published episode's `generated_at`
- Add a topic dedup filter as a new pipeline step (cheap LLM call) that clusters candidate articles by topic, deduplicates against recent episode articles, selects top 3 per topic, and annotates continuations with `[FOLLOW-UP: ...]` context for the composer
- Remove the `recapBlock` from composer prompts — the dedup filter's continuation annotations replace it. Recaps remain for publication descriptions only (feed.xml, SoundCloud)
- Consolidate article eligibility and lifecycle logic into a single component — currently duplicated across `LlmPipeline.run()`, `recompose()`, `preview()`, and `EpisodeService.discardAndResetArticles()`

## Capabilities

### New Capabilities

- `article-dedup-filter`: Topic clustering and deduplication of candidate articles against recent episode history before composition. Handles cross-source dedup within a batch, historical overlap detection, and continuation annotation for follow-up stories.
- `article-eligibility`: Centralized article selection and lifecycle management — age gate, processing state, and discard reset guards. Single source of truth replacing duplicated logic across pipeline code paths.

### Modified Capabilities

- `episode-continuity`: Remove recapBlock from composer prompts. Continuation context now comes from dedup filter annotations (`[FOLLOW-UP: ...]` headers) instead of compressed episode recaps.
- `episode-review`: Discard action guards article reset — only resets `isProcessed` for articles not linked to any published GENERATED episode.
- `llm-processing`: Pipeline delegates article selection to the new article-eligibility component and runs the dedup filter before composition.

## Impact

- `LlmPipeline.kt` — delegate article selection to new component, add dedup filter step before composition, remove recap fetching for composer
- `EpisodeService.kt` — fix `discardAndResetArticles()` to check for published GENERATED episodes before resetting
- `BriefingComposer.kt`, `DialogueComposer.kt`, `InterviewComposer.kt` — remove `previousEpisodeRecaps` parameter and recapBlock, accept dedup filter annotations instead
- New `ArticleEligibilityService` (or similar) — centralized article selection with age gate and processing state logic
- New `TopicDedupFilter` — LLM-based clustering, dedup, and continuation detection
- `EpisodeRepository.kt` — query for latest published episode timestamp
- `ArticleRepository.kt` — age-gated article queries
- Tests — update composer tests, pipeline tests, add dedup filter tests, add eligibility service tests
