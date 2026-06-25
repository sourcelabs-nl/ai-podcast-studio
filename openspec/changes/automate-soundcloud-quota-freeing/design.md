## Context

SoundCloud free accounts have a finite upload-time quota. The previous design did a pre-flight quota check and, when full, threw `SoundCloudQuotaExceededException` (mapped to HTTP 413) carrying a deletion plan. The frontend showed the plan and the user clicked "Remove & publish", which hit `POST .../publish/{target}/free-and-publish`: it deleted the chosen tracks and then re-called `publish()`, which re-ran the pre-flight check. That re-check read `/me` immediately after deletion and got a **stale** quota value (SoundCloud does not reflect freed upload time instantly), so it threw the quota error again and the episode never published. Auto-publish-on-generate had no quota handling and just failed. Republish (Publications tab) had no quota handling either.

## Goals / Non-Goals

**Goals:**
- Free quota automatically, server-side, with no user consent.
- Delete only this podcast's tracks, oldest-first, just enough to fit (plus a small buffer).
- Make manual publish, republish, and auto-publish behave identically.
- Survive SoundCloud's post-deletion quota lag.

**Non-Goals:**
- Reactively parsing SoundCloud's upload-rejection error to detect quota (the `/me` quota numbers are the reliable signal; SoundCloud's 429 is rate-limiting, not upload quota).
- Re-reading quota after deletion to "confirm" freed space (that is exactly what produced the stale-read bug).
- A configurable wait or per-user quota policy.

## Decisions

- **Single accurate read, then size deletion from it.** `freeQuotaIfNeeded` reads `/me` once before any deletion. Deletion is sized as `secondsToFree = requiredSeconds - uploadSecondsLeft + 120s`. Because the read precedes deletion it is accurate; we never re-read afterward, sidestepping the stale-quota trap.
- **Fixed settle wait via `delay`.** After deleting at least one track we `delay(4000)` so SoundCloud registers the freed space, then upload. 4s is empirically enough and avoids a re-read.
- **`suspend` the publish path.** To use a cooperative `delay` instead of `Thread.sleep`, `EpisodePublisher.publish`, `SoundCloudPublisher.publish`/`freeQuotaIfNeeded`, `FtpPublisher.publish`, `PublishingService.publish`, and the `PublishingController.publish` handler are `suspend`. `AutoPublishListener` already runs on a `Dispatchers.IO` coroutine; Spring MVC bridges the suspend controller handler. `FtpPublisher.update` (non-suspend interface method) bridges to the suspend `publish` via `runBlocking`.
- **Remove the consent flow entirely.** The `free-and-publish` endpoint, `freeQuotaAndPublish`, `SoundCloudQuotaExceededException`/`QuotaDeletionPlan`/`QuotaTrackToDelete`, `FreeQuotaRequest`, the 413 handler, and the wizard's quota step are deleted. Every caller now hits the one automatic path.

## Risks / Trade-offs

- **Irreversible deletion without per-action consent.** Oldest tracks are permanently removed. Mitigated by deleting only this podcast's tracks, oldest-first, and only just enough.
- **4s blocking-ish wait.** On the servlet path the request takes ~4s longer when freeing is needed; acceptable for an admin tool. The wait is a cooperative `delay`, so on `Dispatchers.IO` it does not pin a thread.
- **Fixed 4s may be too short** if SoundCloud lag spikes. If it proves flaky, the next step is a bounded retry of the upload after an additional `delay`, still without re-reading quota.
- **Duration estimate accuracy.** Sizing uses `episode.durationSeconds`; the 120s buffer absorbs rounding. When duration is unknown we fall back to "free when `uploadSecondsLeft <= 0`".
