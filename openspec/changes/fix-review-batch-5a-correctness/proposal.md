# Proposal

## Why

The code review of 2026-10-01 listed four defects in how the generation pipeline finishes and reports itself. A failure after the audio exists (a feed export, an event listener) can mark a finished episode FAILED, and its retry then pays for the same text-to-speech again. The pipeline status endpoint always answers `null`, because its tracker listens for an event nothing publishes. And the article aggregator's race recovery cannot work: an exception caught after a repository call still leaves the surrounding transaction rollback-only, so the commit fails.

## What Changes

- A failing static-feed export after audio generation is logged instead of failing the episode (`TtsPipeline`, both entry points).
- A failing listener on `episode.generated` or `episode.created` is logged instead of failing the episode (`AudioGenerationService`, `EpisodeService`).
- A retry that resumes after the audio was already produced keeps that audio and skips text-to-speech.
- `GET .../pipeline-status` reads the stage persisted on the podcast's running episode, and `PipelineStateTracker` is removed.
- The aggregator stores articles with `INSERT OR IGNORE` and re-reads, so a lost insert race resolves inside SQLite and the transaction commits.

## Capabilities

### New Capabilities

### Modified Capabilities
- `pipeline-progress`: the pipeline status is derived from the persisted stage of the running episode, not from an in-memory event listener.
- `pipeline-retry`: a retry resuming after the audio exists does not synthesize it again.

## Impact

- `tts/TtsPipeline.kt`, `podcast/AudioGenerationService.kt`, `podcast/EpisodeService.kt`, `podcast/PodcastController.kt`, `podcast/PipelineStateTracker.kt` (removed), `source/SourceAggregator.kt`, `store/ArticleRepository.kt`, `store/EpisodeRepository.kt`.
- No schema change.
