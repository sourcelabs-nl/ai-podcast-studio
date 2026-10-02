# Proposal

## Why

The code review of 2026-10-01 found a handful of small, local defects: a failed unpublish still discards the record that names the remote asset, cancelled coroutines are logged or persisted as failures, the OAuth callback pages reflect unescaped query input, and the retry path of the LLM cache writes an entry nothing can read. None needs a design change, and fixing them first keeps the larger refactors of the review from being mixed with bug fixes.

## What Changes

- A failed unpublish (same-day replacement and manual unpublish) leaves the publication record untouched, with the failure recorded in `error_message`, instead of deleting the record or clearing `external_id`. A manual unpublish reports the failure to the caller.
- Side effects that run after a successful unpublish (playlist rebuild, post-publish hook, static feed export) can no longer turn a completed unpublish into a failed request.
- `CancellationException` is rethrown ahead of every generic `catch (e: Exception)` in suspend code, so cancellation is no longer persisted as `FAILED`, answered with a 500, streamed as an error event, or logged as an unexpected error.
- The OAuth callback pages HTML-escape the message they render.
- `CachingChatModel` stores the response to a role-tag correction retry under the key of the prompt it corrects, replacing the rejected response, instead of under a key nothing can read. A regenerate then replays the corrected script instead of the rejected one.
- The KDoc of `hasSpeakerTag` is moved back above that function.

## Capabilities

### New Capabilities

### Modified Capabilities
- `llm-cache`: a correction retry's response replaces the rejected response under the original key.
- `episode-publishing`: a failed unpublish keeps the publication record and its external id, for both the same-day replacement and the manual unpublish endpoint.

## Impact

- `publishing/PublishingService.kt`, `publishing/SoundCloudOAuthController.kt`, `publishing/XOAuthController.kt` (or their actual packages), `llm/CachingChatModel.kt`, `llm/RoleTagValidationAdvisor.kt`.
- Cancellation handling in `AudioGenerationService`, `EpisodeController`, `PreviewAudioController`, `PodcastController`, `ArticleScoreSummarizer`, `AutoPublishListener`, `EpisodeService`, `BriefingGenerationScheduler` and `SourcePollingScheduler`.
- Unit tests for each changed behavior. No API shape, schema or migration changes.
