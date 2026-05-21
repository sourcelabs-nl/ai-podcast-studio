## Why

Three production bugs were discovered in the podcast pipeline: (1) articles are marked as `is_processed = true` inside `LlmPipeline.run()` before they are linked to the episode in `EpisodeService`, creating orphaned articles that are permanently lost to future pipeline runs if anything fails between these two operations; (2) the pronunciation guide instructs the LLM to use IPA only on the first occurrence of each term, causing TTS mispronunciation on subsequent occurrences (e.g., "Sourcelabs" in closing notes); (3) there is no way to regenerate the recap/show-notes for an existing episode, so if recap generation fails silently the feed description falls back to raw script text.

## What Changes

- Move the article `is_processed = true` marking from `LlmPipeline.run()` into `EpisodeService.createEpisodeFromPipelineResult()`, so articles are only marked as processed after they are successfully linked to the episode via `episode_articles`.
- Change the pronunciation guide prompt in `InworldTtsProvider` to instruct the LLM to use IPA phoneme notation on **every** occurrence of each term, not just the first.
- Add an endpoint to regenerate the recap and show-notes for an existing episode, so that episodes with missing recaps can be fixed without regenerating the entire episode.
- Change episode sources file from markdown `.txt` to styled HTML with proper UTF-8 charset, clickable links, and clean browser rendering.

## Capabilities

### New Capabilities
- `episode-recap-regeneration`: API endpoint to regenerate recap and show-notes for an existing episode.

### Modified Capabilities
- `episode-article-tracking`: Articles must only be marked as processed after being linked to an episode, not before.
- `pronunciation-dictionary`: IPA phoneme notation must be applied on every occurrence of a pronunciation term, not just the first.
- `episode-sources-file`: Sources file changed from markdown `.txt` to styled HTML (`.html`) with UTF-8 charset declaration.

## Impact

- `LlmPipeline.kt`: Remove the article `isProcessed = true` marking loop.
- `EpisodeService.kt`: Add article processing marking after `saveEpisodeArticleLinks`. Add `regenerateRecap` method.
- `EpisodeController.kt`: New POST endpoint for recap regeneration.
- `InworldTtsProvider.kt`: Update `scriptGuidelines` pronunciation prompt text.
- `EpisodeSourcesGenerator.kt`: Generate HTML instead of markdown, change extension to `.html`.
- `FeedGenerator.kt`: Update sources link extension from `.txt` to `.html`.
- Tests: Update `LlmPipelineTest`, `EpisodeServiceTest`, `InworldTtsProviderTest`, `EpisodeSourcesGeneratorTest`, `FeedGeneratorTest`, `StaticFeedExporterTest`, and add tests for the new endpoint.
