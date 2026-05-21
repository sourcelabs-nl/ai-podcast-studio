## 1. Article-episode atomicity fix

- [x] 1.1 Remove `isProcessed = true` marking loop from `LlmPipeline.run()` (lines 144-146)
- [x] 1.2 Add article processing marking in `EpisodeService.createEpisodeFromPipelineResult()` after `saveEpisodeArticleLinks()`
- [x] 1.3 Update `LlmPipelineTest` to verify pipeline no longer marks articles as processed
- [x] 1.4 Update `EpisodeServiceTest` to verify articles are marked as processed after linking

## 2. Pronunciation guide fix

- [x] 2.1 Update `InworldTtsProvider.scriptGuidelines()` pronunciation prompt to use IPA on every occurrence
- [x] 2.2 Update `InworldTtsProviderTest` to verify the new prompt text

## 3. Recap regeneration endpoint

- [x] 3.1 Add `regenerateRecap()` method to `EpisodeService` that generates recap, show-notes, sources file, and re-exports static feed
- [x] 3.2 Add `POST .../episodes/{episodeId}/regenerate-recap` endpoint to `EpisodeController`
- [x] 3.3 Add controller test for the new endpoint

## 4. Sources file HTML conversion

- [x] 4.1 Convert `EpisodeSourcesGenerator` from markdown `.txt` to styled HTML `.html` with UTF-8 charset
- [x] 4.2 Update `FeedGenerator` sources link extension from `.txt` to `.html`
- [x] 4.3 Update `EpisodeSourcesGeneratorTest`, `FeedGeneratorTest`, `StaticFeedExporterTest`
