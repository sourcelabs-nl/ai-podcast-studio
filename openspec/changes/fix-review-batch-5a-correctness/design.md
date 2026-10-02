# Design

## Context

See proposal.md. The retry path resumes at `POST_COMPOSE` through `EpisodeService.finalizeEpisode`, which synthesizes audio, marks articles processed, generates the epilogue, advances `lastGeneratedAt` and publishes an event, inside the one try that `PipelineRunner` turns into FAILED.

## Goals / Non-Goals

**Goals:** a failure of a notification or side effect never fails an episode whose audio exists; a retry never pays for audio it already has; the status endpoint reports what is running; the aggregator's race recovery works.

**Non-Goals:** narrowing the `PipelineRunner` try block (a failure of the bookkeeping steps between audio and completion still fails the episode, and the retry now resumes cheaply), restructuring `finalizeEpisode` and `createEpisodeFromPipelineResult`.

## Decisions

- **Notifications and exports are guarded where they run.** Feed export and event publication are logged on failure, with cancellation rethrown. They cannot undo the audio, mirroring what `PublishingService` already does for publish side effects.
- **Resume keeps audio.** `finalizeEpisode` skips synthesis when the episode already has an audio file on disk and saves it as GENERATED. Alternative: guard every post-audio step individually, rejected because a real failure there should still surface and the cheap retry removes the cost.
- **Status from persisted state.** The endpoint reads `pipeline_stage` of the podcast's newest GENERATING episode. It survives a restart, and an episode deleted for having nothing to compose takes its stage with it, which the in-memory tracker would have left stuck. Regeneration runs do not persist a stage, so they report idle. Alternative: fix the tracker's event name, rejected for the stuck-state case.
- **`INSERT OR IGNORE` for aggregated articles.** An exception caught after a repository call cannot be recovered from inside the transaction, and `REQUIRES_NEW` would deadlock on SQLite's single writer. The insert ignores the unique conflict and the aggregator re-reads. A NOT NULL refusal is also ignored by SQLite, which the re-read detects.

## Risks / Trade-offs

- [A resumed episode keeps audio made from an older script] -> A retry never edits the script (script edits are only allowed in review), so the script and audio match.
- [Regeneration shows no stage in the status endpoint] -> The SSE stage events still carry it live.
