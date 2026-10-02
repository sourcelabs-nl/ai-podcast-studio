# Spec Delta

## MODIFIED Requirements

### Requirement: Same-day publication replacement
When publishing an episode to a target, the system SHALL check for existing publications from other episodes in the same podcast on the same target that share the same `generatedAt` date (YYYY-MM-DD). For each such publication, the system SHALL unpublish the old content from the external platform (e.g. delete the SoundCloud track) and delete the publication record before proceeding with the new publication. When unpublishing the old content fails, the system SHALL keep the old publication record unchanged except for recording the failure in `error_message`, and SHALL still proceed with the new publication.

#### Scenario: Publish regenerated episode replaces original
- **WHEN** episode 45 (generatedAt 2026-03-16) is published to SoundCloud, and episode 41 (generatedAt 2026-03-16) is already published to SoundCloud
- **THEN** the system deletes episode 41's SoundCloud track, removes its publication record, and publishes episode 45 as a new track

#### Scenario: Publish episode does not replace different-day episodes
- **WHEN** episode 45 (generatedAt 2026-03-16) is published to SoundCloud, and episode 32 (generatedAt 2026-03-05) is published to SoundCloud
- **THEN** episode 32's publication is not affected

#### Scenario: Unpublish failure does not block new publication
- **WHEN** unpublishing the old track fails
- **THEN** the system logs a warning, keeps the old publication record with its `external_id` and status, records the failure in `error_message`, and proceeds with publishing the new episode

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
- **WHEN** a user unpublishes an episode from a target and the removal from the platform succeeds
- **THEN** the record is updated to status `UNPUBLISHED` and `external_id` is cleared

#### Scenario: Publication record kept when unpublish fails
- **WHEN** a user unpublishes an episode from a target and the removal from the platform fails
- **THEN** the record keeps status `PUBLISHED` and its `external_id`, and `error_message` records the failure reason

#### Scenario: Update records the publisher's new external id
- **WHEN** an update returns an `external_id` different from the stored one
- **THEN** the record is saved with the returned `external_id` and `external_url`

#### Scenario: FTP identifier follows the regenerated audio filename
- **WHEN** an episode's audio is regenerated and the episode is republished to FTP
- **THEN** the record's `external_id` matches the slug of the new audio file rather than the previous one

### Requirement: Unpublish episode from target endpoint
The system SHALL provide a `DELETE /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/publications/{target}` endpoint. The endpoint SHALL verify the episode has a PUBLISHED publication for the given target, delegate removal to the publisher, update the publication status to `UNPUBLISHED`, clear the `externalId`, and return the updated publication record. When the removal fails, the endpoint SHALL leave the publication unchanged apart from `error_message` and return an error response. Failures in the follow-up work after a successful removal (playlist rebuild, feed export) SHALL be logged and SHALL NOT change the outcome of the request.

#### Scenario: Successful unpublish from SoundCloud
- **WHEN** a `DELETE .../publications/soundcloud` request is received for an episode with a PUBLISHED SoundCloud publication
- **THEN** the system deletes the track from SoundCloud, rebuilds the playlist, updates the publication status to `UNPUBLISHED`, clears `external_id`, publishes an `episode.unpublished` SSE event, and returns HTTP 200 with the updated publication record

#### Scenario: Successful unpublish from FTP
- **WHEN** a `DELETE .../publications/ftp` request is received for an episode with a PUBLISHED FTP publication
- **THEN** the system deletes the MP3 file from the FTP server, regenerates and uploads feed.xml, updates the publication status to `UNPUBLISHED`, clears `external_id`, publishes an `episode.unpublished` SSE event, and returns HTTP 200

#### Scenario: Removal from the platform fails
- **WHEN** a `DELETE .../publications/soundcloud` request is received and deleting the track fails
- **THEN** the publication keeps status `PUBLISHED` and its `external_id`, `error_message` records the failure, no `episode.unpublished` event is published, and the system returns an error response

#### Scenario: Follow-up work fails after a successful removal
- **WHEN** the track was removed and the publication is `UNPUBLISHED`, but the playlist rebuild or feed export fails
- **THEN** the failure is logged, the `episode.unpublished` event is still published, and the request returns HTTP 200

#### Scenario: Episode not published to target
- **WHEN** a `DELETE .../publications/soundcloud` request is received for an episode that is not PUBLISHED to SoundCloud
- **THEN** the system returns HTTP 404

#### Scenario: Unknown target
- **WHEN** a `DELETE .../publications/youtube` request is received for an unsupported target
- **THEN** the system returns HTTP 400

#### Scenario: Episode not found
- **WHEN** a `DELETE .../publications/soundcloud` request is received for an episode that does not exist
- **THEN** the system returns HTTP 404
