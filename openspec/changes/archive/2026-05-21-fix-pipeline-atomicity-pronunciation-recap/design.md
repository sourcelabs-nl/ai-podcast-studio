## Context

Three production bugs were discovered:

1. **Article-episode atomicity gap**: `LlmPipeline.run()` marks articles as `is_processed = true` (line 144-146) before returning `PipelineResult` to `EpisodeService.createEpisodeFromPipelineResult()`, where the episode-article links are saved. If anything fails between these two operations, articles become permanently orphaned: processed but unlinked, invisible to future pipeline runs.

2. **Pronunciation on subsequent occurrences**: The pronunciation guide in `InworldTtsProvider` instructs the LLM to use IPA only on the first occurrence of each term. The TTS engine mispronounces terms on subsequent occurrences where normal spelling is used (e.g., "Sourcelabs" in closing notes).

3. **No recap regeneration**: If `generateAndStoreRecap` fails silently (caught by the try/catch at `EpisodeService.kt:134`), the episode has `recap = NULL` and `show_notes = NULL`. The feed description falls back to raw script text. There is no way to regenerate the recap without regenerating the entire episode.

## Goals / Non-Goals

**Goals:**
- Ensure articles are only marked as processed after being successfully linked to an episode
- Ensure TTS correctly pronounces all dictionary terms on every occurrence
- Allow recap/show-notes regeneration for existing episodes

**Non-Goals:**
- Adding transactional guarantees across the entire pipeline (scoring, dedup, composition)
- Changing the recap generation logic itself (only adding a retry path)
- Fixing orphaned articles from past episodes (data repair is a manual operation)

## Decisions

### D1: Move `isProcessed` marking into EpisodeService

Remove the `isProcessed = true` loop from `LlmPipeline.run()` (lines 144-146). Add it to `EpisodeService.createEpisodeFromPipelineResult()` immediately after `saveEpisodeArticleLinks()`. The `PipelineResult.processedArticleIds` already carries the article IDs, so `EpisodeService` can look up and mark each article.

**Alternative considered**: Wrapping both operations in a `@Transactional` method. Rejected because the pipeline runs LLM calls (long-running, non-transactional) and mixing transaction boundaries with HTTP calls to external services is fragile. Moving the marking into `EpisodeService` achieves the same atomicity without transaction complexity.

### D2: Use IPA on every occurrence

Change the pronunciation guide prompt from "REPLACE the word with its IPA phoneme notation on the FIRST occurrence" to "REPLACE the word with its IPA phoneme notation on EVERY occurrence". This is the simplest fix and ensures the TTS engine always receives the correct phoneme.

**Alternative considered**: Using SSML or TTS-level pronunciation dictionaries. Rejected because the Inworld TTS API does not support custom pronunciation dictionaries, and SSML support varies across providers.

### D3: Add recap regeneration as a POST endpoint

Add `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/regenerate-recap` that calls the existing `EpisodeRecapGenerator`, updates the episode's `recap` and `show_notes`, and regenerates the sources file. Reuse the existing `generateAndStoreRecap` and `generateAndStoreShowNotes` private methods by extracting them or making the regeneration method call them directly.

The endpoint SHALL also re-export the static feed so the fixed description is immediately reflected.

## Risks / Trade-offs

- **[Risk] Existing orphaned articles remain lost** → Manual data fix required. Out of scope for this change. The user can manually reset `is_processed = 0` for specific articles and regenerate.
- **[Risk] IPA on every occurrence increases token usage slightly** → Negligible impact. Pronunciation terms are short and few per episode.
- **[Risk] Recap regeneration uses LLM call, costs money** → Acceptable since it's a manual, on-demand operation.
