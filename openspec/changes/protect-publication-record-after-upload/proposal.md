## Why

A publication record can end up denying a publish that actually happened, and the lie is not recoverable through the application.

Episode 202 was uploaded to SoundCloud on 9 September:

```
Episode 202 published to soundcloud (externalId=2397318972)
Updating SoundCloud track 2397318972 permalink to the-daily-agentic-ai-podcast-2026-09-09
```

Its row nevertheless ended as `status=FAILED, external_id=NULL, error_message="MonoCoroutine was cancelled"`. Everything after the upload ran inside the same `try` as the upload itself: the publisher's `postPublish` hook, the SoundCloud playlist rebuild, the static feed export, the `episode.published` event. One of them was cancelled, and the catch wrote `publication.copy(status = FAILED, ...)`, where `publication` is the snapshot taken *before* the upload. That snapshot carries no external id, so saving it discarded the track id.

The damage surfaced a day later. Episode 202 was regenerated as episode 206 for the same date, and the same-day replacement in `publish()` scans only rows with status `PUBLISHED`. There was no such row, so the old track was never deleted, and SoundCloud rejected the new upload:

```
Failed to publish episode 206 to soundcloud: 400 Bad Request ... "permalink has already been taken"
```

The publication could only be completed after restoring the row by hand. Two separate faults produced that:

1. Steps that cannot undo the upload were allowed to overrule its result.
2. `CancellationException` was treated as a publishing failure. A cancelled call proves nothing about whether the upload landed, so recording `FAILED` with a null external id asserts something the system does not know.

## What Changes

- `PublishingService.publish()` SHALL treat only `publisher.publish()` as the step that can fail the publication. It runs in its own `try`; the record is saved as `PUBLISHED` immediately after it returns, and every later step runs outside that block.
- The post-publish side effects (the publisher's `postPublish` hook, the SoundCloud playlist rebuild, the static feed export) SHALL each be guarded individually. A failure is logged and the publication keeps the external id it just earned.
- `CancellationException` SHALL be rethrown untouched from both `publish()` and `updateExisting()`, leaving the record as it stands rather than recording a failure.
- `updateExisting()` SHALL follow the same shape: the update call in its own `try`, the new external id persisted, the side effects afterwards.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `episode-publishing`: gains a requirement stating that a successful upload's record survives any later step, and that a cancelled publish is not recorded as a failure.

## Impact

- Backend: `PublishingService.publish()` and `PublishingService.updateExisting()` restructured; new private `runPostPublishSideEffects` and `runSideEffect` helpers replace the inline calls in both.
- Behaviour change worth noting: a publish whose feed export or `postPublish` hook fails now returns successfully with a `PUBLISHED` record, where before it threw and marked the record `FAILED`. The failure is visible as a WARN naming the step. That is the point: none of these steps can undo an upload, so none of them may deny it.
- Tests: `PublishingServiceTest` gains 4 cases.
- Episode 202's row was repaired by hand (`status=PUBLISHED, external_id=2397318972`) so that publishing episode 206 could clean up the old track through the normal path. Its FTP row is left `FAILED` on purpose: promoting it would put a second entry for 9 September in the feed, since episode 206 already holds one. The superseded remote file `briefing-20260909-140250.mp3` stays behind as an orphan.
- No schema, API, frontend, or configuration change.
