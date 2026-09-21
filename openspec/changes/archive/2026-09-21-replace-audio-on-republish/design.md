## Context

`EpisodePublisher.update()` was introduced for metadata: refresh a description after show notes change. Audio was assumed immutable once published. Audio regeneration broke that assumption, and `update()` kept its old meaning while callers started using it to mean "make the platform match the episode as it is now".

The two publishers sit at opposite ends of what a platform allows. FTP is a filesystem, so `update()` is literally `publish()`: upload over the old path and the old audio is gone. SoundCloud is a media host that treats a track as an immutable audio object with mutable metadata, so "same track, new audio" is not expressible.

## Goals / Non-Goals

**Goals:**

- Republishing makes the platform serve the episode's current audio, on every target.
- The stored publication row identifies whatever now exists on the platform.
- The episode keeps its canonical SoundCloud URL across a replacement.

**Non-Goals:**

- Preserving a replaced track's play count, likes or comments. SoundCloud deletes those with the track and offers no transfer, so a replacement necessarily resets them. Worth accepting: the alternative is publishing audio the show has disowned.
- A migration for the two stale `external_id` rows. Both self-heal on their next publish, and a migration would have to re-derive slugs from audio paths outside the code that owns that derivation.
- Detecting whether the audio actually changed before replacing. `update()` has no cheap way to compare local audio against a remote track (no checksum is exposed), and the caller already only reaches this path when it wants the platform refreshed.

## Decisions

**Delete then upload, not upload then delete.**

Uploading first would be safer against data loss, but produces the wrong result twice over. SoundCloud reserves a permalink while its track exists, so the new upload would be slugged `…-2026-08-21-1` and the episode's canonical URL would still point at the stale track until the delete landed. And on a quota-limited account the upload would be rejected outright, because the space it needs is held by the track being replaced. Deleting first was also what the manual repair of episode 184 did, and the replacement did reclaim the original permalink.

**The quota pause belongs in `update()`, not only in `freeQuotaIfNeeded`.**

`freeQuotaIfNeeded` already waits `QUOTA_SETTLE_MILLIS` after deleting tracks, because SoundCloud does not reflect freed seconds immediately. `update()` performs a deletion that same function knows nothing about, then calls `publish()`, which reads the quota. Without a pause that read can still show the pre-delete figure, `freeQuotaIfNeeded` concludes there is no room, and it deletes *older episodes* to free space the replacement had already freed. Replacing one track would silently cost several. The pause is what keeps the blast radius at one track.

**No new "supports audio replacement" capability on the interface.**

Considered adding a flag so `PublishingService` could branch to unpublish-then-publish for platforms that cannot replace in place. Rejected: it pushes platform mechanics into the service and forces the service to own two-phase bookkeeping across a repository and a remote API. Keeping the delete inside `SoundCloudPublisher.update()` leaves the service with one contract — "call update, persist what comes back" — and lets each publisher express replacement however its platform allows. `update()` remaining `UnsupportedOperationException` by default still covers publishers that cannot do it at all.

**Persist `result.externalId` rather than special-casing SoundCloud.**

The service does not need to know which targets change identity. Saving whatever the publisher returns is correct for all of them, and it fixes the FTP staleness as a side effect rather than as a second patch.

## Risks / Trade-offs

- **A delete that succeeds followed by an upload that fails leaves the episode unpublished, where before a failed update left the old audio live.** → Accepted, and made recoverable: `SoundCloudClient.deleteTrack` already treats a 404 as done, so `update()` is idempotent and a retry completes the replacement. The publication row keeps `PUBLISHED` with the dead id in that window, which is wrong but self-correcting on retry. Tightening the failure path to mark the row `FAILED` would touch every target's error handling and is left out of this change.
- **Republishing is now expensive: a full upload instead of a metadata PATCH.** → Unavoidable if the audio is to change. Nothing calls `update()` on a schedule; it runs on explicit publish and on auto-publish after generation.
- **Play counts reset on replacement.** → See Non-Goals. Regeneration is rare and usually same-day, so the loss is small in practice.
- **`QUOTA_SETTLE_MILLIS` is a fixed 4s guess at someone else's eventual consistency.** → Pre-existing constant, reused rather than re-tuned. If SoundCloud gets slower the symptom is over-deletion by `freeQuotaIfNeeded`, which its own logging already makes visible.
