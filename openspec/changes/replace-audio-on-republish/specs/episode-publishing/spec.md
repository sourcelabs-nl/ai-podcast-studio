## MODIFIED Requirements

### Requirement: Publisher update interface
The `EpisodePublisher` interface SHALL include an `update(episode, podcast, userId, externalId)` method that brings an already-published episode on the external platform into line with the episode as it currently stands, including its audio. The default implementation SHALL throw `UnsupportedOperationException`. Publishers that support updates SHALL override this method.

An update SHALL NOT be assumed to preserve the episode's external identity. A platform that cannot replace a published asset in place SHALL express the update as a replacement and return the new identifier, and the caller SHALL persist it.

`SoundCloudPublisher` SHALL implement the update as a replacement: delete the existing track, then upload the episode's current audio. SoundCloud exposes no way to swap a track's audio in place, so a metadata-only update would report success while leaving superseded audio live. The deletion SHALL precede the upload, because SoundCloud reserves a track's permalink for as long as the track exists (freeing it first lets the replacement claim the episode's canonical URL rather than a suffixed variant) and because the deletion returns the old track's seconds to the upload quota. A `QUOTA_SETTLE_MILLIS` pause SHALL separate the two, so that the quota check preceding the upload sees the freed space and does not delete further, older episodes to make room that already exists.

`FtpPublisher` SHALL implement the update as a republish, since uploading over the existing remote path replaces the audio directly.

#### Scenario: SoundCloud update replaces the audio
- **WHEN** `SoundCloudPublisher.update()` is called for an episode whose audio has been regenerated
- **THEN** the existing track is deleted, the current audio is uploaded as a new track, and the new track id is returned

#### Scenario: SoundCloud update does not fall back to a metadata-only change
- **WHEN** `SoundCloudPublisher.update()` runs
- **THEN** no metadata-only track update is issued in place of the replacement

#### Scenario: Replacement keeps the canonical permalink
- **WHEN** a track is replaced for an episode generated on 2026-02-13 for the podcast "Tech News"
- **THEN** the replacement upload requests the permalink `tech-news-2026-02-13`

#### Scenario: Replacement carries the current description
- **WHEN** an episode with show notes is republished to SoundCloud
- **THEN** the replacement upload's description is built from those show notes, falling back to the recap when show notes are absent

#### Scenario: Republish is retryable after a failed upload
- **WHEN** a replacement deleted the old track but failed to upload, and the republish is retried
- **THEN** the delete of the already-removed track is treated as done and the upload proceeds

#### Scenario: Publisher does not support update
- **WHEN** `update()` is called on a publisher that has not overridden the default
- **THEN** an `UnsupportedOperationException` is thrown and the endpoint returns HTTP 400

### Requirement: Publication status tracking
The system SHALL store publication records in an `episode_publications` table with columns: `id` (INTEGER, auto-increment PK), `episode_id` (INTEGER, FK to episodes), `target` (TEXT, NOT NULL), `status` (TEXT, NOT NULL — PENDING, PUBLISHED, FAILED, UNPUBLISHED), `external_id` (TEXT, nullable), `external_url` (TEXT, nullable), `error_message` (TEXT, nullable), `published_at` (TEXT, nullable — ISO-8601), `created_at` (TEXT, NOT NULL — ISO-8601). A unique constraint SHALL exist on `(episode_id, target)`.

When an already-published episode is updated, the system SHALL persist the `external_id` and `external_url` returned by the publisher, not the values the record already held. Both can change on an update: a SoundCloud replacement is a new track, and the FTP identifier is derived from the audio filename, which changes whenever the episode is re-synthesized. Retaining the previous identifier leaves the record naming an asset that no longer exists.

#### Scenario: Publication record created on publish attempt
- **WHEN** a user triggers publishing an episode to SoundCloud
- **THEN** a record is created in `episode_publications` with status `PENDING` and the `created_at` timestamp set

#### Scenario: Publication record updated on success
- **WHEN** the SoundCloud upload completes successfully
- **THEN** the record is updated to status `PUBLISHED` with `external_id`, `external_url`, and `published_at` set

#### Scenario: Publication record updated on failure
- **WHEN** the SoundCloud upload fails
- **THEN** the record is updated to status `FAILED` with `error_message` containing the failure reason

#### Scenario: Publication record updated on unpublish
- **WHEN** a user unpublishes an episode from a target
- **THEN** the record is updated to status `UNPUBLISHED` and `external_id` is cleared

#### Scenario: Update records the publisher's new external id
- **WHEN** an update returns an `external_id` different from the stored one
- **THEN** the record is saved with the returned `external_id` and `external_url`

#### Scenario: FTP identifier follows the regenerated audio filename
- **WHEN** an episode's audio is regenerated and the episode is republished to FTP
- **THEN** the record's `external_id` matches the slug of the new audio file rather than the previous one
