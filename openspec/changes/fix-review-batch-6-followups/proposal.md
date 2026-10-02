# Proposal

## Why

The closing code review of the batch series left a few findings that are direct defects or leftovers of the earlier batches: a SoundCloud replacement loses the new track id when claiming the permalink fails, 14 controller sites still build their own 400 body although the API now has one error contract, the script judge blocks the request thread, the auto-publish scope is never cancelled, and one method holds a database transaction across file I/O.

## What Changes

- `SoundCloudPublisher.update` returns the new track when claiming the canonical permalink fails, instead of throwing after the old track is already deleted.
- The remaining hand-built 400 responses throw `InvalidRequestException`, and the page and pageSize check is shared.
- `ScriptJudge` runs its blocking LLM call on `Dispatchers.IO` and rethrows cancellation.
- `AutoPublishListener` cancels its coroutine scope on shutdown.
- `EpisodeService.regenerateSourcesHtml` is no longer `@Transactional`.

Status codes and bodies of the controllers stay the same.

## Capabilities

### New Capabilities

### Modified Capabilities
- `soundcloud-integration`: a replacement whose permalink claim fails still reports its new track id.

## Impact

- `publishing/SoundCloudPublisher.kt`, `eval/ScriptJudge.kt`, `publishing/AutoPublishListener.kt`, `podcast/EpisodeService.kt`, and the controllers that build 400 bodies.
