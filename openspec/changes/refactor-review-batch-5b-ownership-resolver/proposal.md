# Proposal

## Why

The code review of 2026-10-01 found the check "the user exists, the podcast belongs to the user, the episode belongs to the podcast" copy-pasted about 40 times across the podcast-scoped controllers, with drifting helper variants in `ExperimentController`, `PreviewAudioController`, `ScriptMetricsController` and `EpisodeScoringController`. The rule decides who may see what, so a variant that drifts is a security defect waiting to happen.

## What Changes

- One `PodcastAccess` component resolves the user, the podcast and the episode of a request and throws a `ResourceNotFoundException` when any of them does not exist or does not belong to the one above it.
- `ApiExceptionHandler` maps `ResourceNotFoundException` to the 404 with an empty body that the controllers returned.
- The podcast-scoped controllers call `PodcastAccess` instead of repeating the lookups, and the drifting local helpers are removed.

Every response stays identical, including the empty 404 body.

## Capabilities

### New Capabilities

### Modified Capabilities

## Impact

- New `podcast/PodcastAccess.kt` and `api/ResourceNotFoundException`, `api/ApiExceptionHandler.kt`.
- `EpisodeController`, `PodcastController`, `SourceController`, `PodcastImageController`, `PodcastPublicationTargetController`, `PublishingController`, `PublishingTestController`, `PodcastPublicationsController`, `PlaylistController`, `PreviewAudioController`, `ExperimentController`, `ScriptMetricsController`, `EpisodeScoringController`.
- Controller tests import `PodcastAccess` and keep mocking the services it uses.
