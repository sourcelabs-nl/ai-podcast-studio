# Proposal

## Why

The code review of 2026-10-01 found nine controllers that catch exceptions inline and build their own error responses (Rule SB8). The mapping is inconsistent by accident: the same `IllegalArgumentException` is a 400 in some controllers and a 422 in another, error bodies differ, and several catches hand an internal exception message to the client. One of them is also a controller that makes an HTTP call to a third party itself.

## What Changes

- A small set of typed API exceptions and one `@RestControllerAdvice` give the API one error contract: body `{"error": message}`, 400 for a malformed request, 422 for content that is well-formed but not acceptable, 502 for a failing upstream service, 500 for a failed operation.
- The services that decide a request is invalid or that an operation failed throw those exceptions (as subclasses of `IllegalArgumentException` and `RuntimeException`, so existing callers that catch the base types keep working).
- The inline `try/catch` blocks leave `PlaylistController`, `PodcastImageController`, `SourceController`, `VoiceController`, `BackupController`, `PreviewAudioController`, `EpisodeController.regenerateRecap` and `SoundCloudOAuthController.deleteTrack`.
- `ResearchTestController` stops calling Tavily itself; a service runs the check and returns the result object.

Every status code and error body that exists today stays the same. The two codes the specs fix (422 for source validation, 502 for voice discovery) are kept.

## Capabilities

### New Capabilities

### Modified Capabilities

## Impact

- New `api/ApiExceptions.kt` and `api/ApiExceptionHandler.kt`.
- `PodcastImageService`, `SourceService`, `PublishingService`, `DatabaseBackupService`, the voice and recap code paths, `ResearchService` and the controllers listed above.
- Controller tests lose their inline-catch cases; the advice gets a test per exception type, and each service gets a test for the exception it now throws.
