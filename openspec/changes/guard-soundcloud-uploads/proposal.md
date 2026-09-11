## Why

On 11 September 2026, SoundCloud began refusing `POST /tracks` with `403 An active Pro Unlimited subscription is required for this action`. The same call had succeeded seven hours earlier from the same account and the same build. The account was inside every published limit at the time: six tracks live, 1610 seconds of upload quota free, and episode 207 needing 784 of them. The token refreshed cleanly on every attempt, so this is neither an authorization failure nor a quota failure.

That refusal exposed a defect in the publisher's ordering. Both paths delete tracks *before* they know an upload can succeed:

- `publish` calls `freeQuotaIfNeeded` first, which permanently deletes the podcast's oldest tracks to make room. On a tight-quota account the back catalogue would be spent on an upload that is refused regardless.
- `update` is worse. It deletes the live track first and then uploads its replacement, so a refused upload takes the published episode down and puts nothing back.

Neither loss is recoverable: `deleteTrack` is permanent, and no quota rejection has ever been observed in the logs, so the deletion path was always preventive rather than a response to a real failure.

## What Changes

- `SoundCloudClient.uploadTrack` translates a `403` whose body reports that a subscription is required into a new `SoundCloudUploadNotPermittedException`. It is not an `HttpClientErrorException`, so it cannot be mistaken for a condition that deleting tracks would fix.
- `publish` attempts the upload first and frees quota only after an attempt has actually been refused, retrying once when at least one track was deleted. `freeQuotaIfNeeded` returns whether it freed anything, so a caller knows when a retry is pointless.
- The refusal is reported as HTTP 403 with `code: "upload_not_permitted"` and SoundCloud's own sentence, so the dashboard shows prose instead of the raw error envelope under a 500.
- `update` uploads the replacement before deleting the track it replaces. The old track holds the canonical permalink during the overlap, so the replacement claims that slug once the old track is gone, and the returned `externalUrl` is the reclaimed canonical URL.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `soundcloud-integration`: the upload requirement gains the subscription-refusal translation; the quota requirement changes from freeing space before the upload to freeing it only after a refusal; a new requirement pins the upload-then-delete ordering of an audio replacement, which was previously only described in code.

## Impact

- Backend: `SoundCloudClient` (new exception type, 403 translation), `SoundCloudPublisher` (`publish`, `update`, `freeQuotaIfNeeded`, new `buildUploadRequest` and `episodeDate` helpers).
- Tests: `SoundCloudPublisherTest`. Three existing tests encoded the old ordering and were updated; two were added for the refusal paths.
- Cost: on a genuinely full account, one upload attempt is now wasted before space is freed. That is once per publish, against the previous risk of losing published episodes for nothing.
- No API request payload change, no database change, no frontend change.
- This does not restore publishing. The 403 is SoundCloud's to lift; the change makes hitting it harmless.
