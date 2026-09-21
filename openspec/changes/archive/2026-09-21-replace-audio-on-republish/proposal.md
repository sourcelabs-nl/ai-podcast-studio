## Why

Republishing an episode whose audio has been regenerated did not deliver the new audio, and reported success anyway.

`SoundCloudPublisher.update()` pushed only the track description, because `SoundCloudClient.updateTrack()` carries metadata (`permalink`, `description`) and nothing else. SoundCloud has no API for swapping a track's audio in place. So after episode 184's script was repaired and its audio regenerated, the auto-republish logged

```
Updated SoundCloud track 2385692019 description for episode 184
Episode 184 updated on soundcloud (externalId=2385692019)
```

while track 2385692019 kept serving the old audio with the truncated opening. The repair had to be finished by hand: delete the track, upload again.

`PublishingService.updateExisting()` compounded it by not persisting the id the publisher returned:

```kotlin
existing.copy(externalUrl = result.externalUrl, publishedAt = ..., errorMessage = null)
```

An update is not obliged to preserve external identity. SoundCloud cannot replace audio in place, so its replacement is necessarily a new track id. The FTP id is `"ftp:$slug"` where the slug is the audio filename (`EpisodeSourcesGenerator.deriveSlug`), so it changes on every re-synthesis. Discarding `result.externalId` left rows pointing at things that no longer exist: two in the database, episode 141 (regenerated in June) and episode 184, both carrying an `external_id` naming an MP3 that had been superseded.

## What Changes

- `SoundCloudPublisher.update()` SHALL replace the track: delete the existing one, then upload the episode's current audio via `publish()`. It SHALL return the new track id.
- The deletion SHALL happen before the upload, and a `QUOTA_SETTLE_MILLIS` pause SHALL separate them. Deleting first frees the permalink so the replacement claims the episode's canonical URL instead of a suffixed variant, and it returns the old track's seconds to the upload quota. Without the pause, `publish()` reads a quota that has not yet registered the deletion and deletes further, older episodes to make room that already exists.
- `PublishingService.updateExisting()` SHALL persist `result.externalId` alongside `result.externalUrl`, so a target that changes external identity on update is recorded correctly.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `episode-publishing`: the publisher-update requirement now covers audio replacement rather than metadata only, and the status-tracking requirement states that an update persists the returned external id.

## Impact

- Backend: `SoundCloudPublisher.update()` (delete-then-upload, replacing the description-only call); `PublishingService.updateExisting()` (persists `externalId`, and logs the new id rather than the old one).
- Tests: `SoundCloudPublisherTest` gains 5 cases and loses the two that encoded the description-only behaviour; `PublishingServiceTest` gains a case pinning that a changed external id is persisted.
- Behaviour change worth noting: republishing to SoundCloud now costs an upload and deletes the previous track, where before it was a cheap metadata call. That is the only way to change the audio, and it is what a republish was always meant to mean.
- No schema, API, frontend, or configuration change.
- Episode 184 was repaired before this change existed (delete plus re-upload by hand, new track `2386214130`, same permalink), and its FTP row was healed by a republish once the service fix was in. Episode 141 still carries a stale FTP `external_id`; it self-heals on its next publish and no data migration is included.
