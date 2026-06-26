## 1. Service layer

- [x] 1.1 Add `updateLastGenerated` flag to `EpisodeService.createGeneratingEpisode` (skip the `lastGeneratedAt` bump when false)
- [x] 1.2 Rename `PodcastService.retryScope` → `pipelineScope` (now hosts generate/regenerate/retry) and its `@PreDestroy`
- [x] 1.3 Extract `runGenerationPipeline(podcast, generatingEpisode)` from `generateBriefing`; keep `generateBriefing` for the scheduler
- [x] 1.4 Add `generateBriefingAsync(podcast): Episode?` (null when an episode is already active) launching on `pipelineScope`
- [x] 1.5 Replace `regenerateEpisode` with `regenerateEpisodeAsync` + `runRegeneration`, creating the GENERATING episode with `updateLastGenerated = false`

## 2. Controllers

- [x] 2.1 `PodcastController.generate` → non-suspend; return 202 with episodeId, or 409 when already generating
- [x] 2.2 `PodcastController.regenerate` → non-suspend; return 202 with the new episodeId

## 3. Verification

- [x] 3.1 `mvn test` green (930 tests)
- [x] 3.2 Restart app; confirm clean boot and polling
