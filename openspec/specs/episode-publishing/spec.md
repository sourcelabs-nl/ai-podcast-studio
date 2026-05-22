# Capability: Episode Publishing

## Purpose

Publish generated podcast episodes to external platforms (e.g., SoundCloud) via a pluggable publisher abstraction with publication status tracking.

## Requirements

### Requirement: Publisher interface and registry
The system SHALL define an `EpisodePublisher` interface with a `publish(episode, podcast, userId)` method that uploads an episode to an external platform and returns a `PublishResult` containing the external ID and URL. Each implementation SHALL be a Spring bean identified by a target name string (e.g., `"soundcloud"`). A `PublisherRegistry` Spring component SHALL collect all `EpisodePublisher` beans and provide a `getPublisher(target): EpisodePublisher?` lookup method.

#### Scenario: Registry resolves known target
- **WHEN** `PublisherRegistry.getPublisher("soundcloud")` is called and a `SoundCloudPublisher` bean exists
- **THEN** the registry returns the `SoundCloudPublisher` instance

#### Scenario: Registry returns null for unknown target
- **WHEN** `PublisherRegistry.getPublisher("youtube")` is called and no publisher is registered for `"youtube"`
- **THEN** the registry returns null

### Requirement: Publisher update interface
The `EpisodePublisher` interface SHALL include an `update(episode, podcast, userId, externalId)` method that updates an already-published episode's metadata on the external platform. The default implementation SHALL throw `UnsupportedOperationException`. Publishers that support metadata updates SHALL override this method.

#### Scenario: Publisher supports update
- **WHEN** `SoundCloudPublisher.update()` is called with an episode and its external track ID
- **THEN** the track's description is updated on SoundCloud and a `PublishResult` is returned

#### Scenario: Publisher does not support update
- **WHEN** `update()` is called on a publisher that has not overridden the default
- **THEN** an `UnsupportedOperationException` is thrown and the endpoint returns HTTP 400

### Requirement: Publish episode to target endpoint
The system SHALL provide a `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/publish/{target}` endpoint. The endpoint SHALL look up the publisher by target name, verify the episode exists with status `GENERATED` and has an audio file, verify that the podcast has an **enabled** `podcast_publication_targets` entry for the requested target, then delegate to the publisher. When the episode has no existing publication for the target, the endpoint SHALL create a new publication. When the episode already has a `PUBLISHED` record for the target, the endpoint SHALL update the existing external resource's metadata and return HTTP 200 with the updated publication record. After the publish operation completes (success or failure), the service SHALL publish a `PodcastEvent` to notify connected clients. On success, it SHALL return HTTP 200 with the publication record. On failure, it SHALL return an appropriate error.

#### Scenario: Successful publish
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/publish/soundcloud` request is received for a GENERATED episode with an audio file, the user has a connected SoundCloud account, and the podcast has an enabled SoundCloud publication target
- **THEN** the system uploads the episode to SoundCloud, creates an `episode_publications` record with status `PUBLISHED`, publishes an `episode.published` event, and returns HTTP 200 with the publication details

#### Scenario: Publish failure
- **WHEN** the publish operation fails for any reason (network error, API error)
- **THEN** the system updates the publication record to status `FAILED` with `error_message`, publishes an `episode.publish.failed` event, and returns an error response

#### Scenario: Republish updates existing publication
- **WHEN** a `POST .../publish/soundcloud` request is received for an episode that already has a `PUBLISHED` record for `"soundcloud"` with `externalId` `"12345"`
- **THEN** the system calls the publisher's update method with the external ID, updates the publication record's `publishedAt` timestamp, and returns HTTP 200 with the updated publication details

#### Scenario: Republish to SoundCloud updates track description
- **WHEN** a `POST .../publish/soundcloud` request is received for an already-published episode that now has show notes
- **THEN** the SoundCloud track description is updated to the episode's current show notes (or recap/script fallback)

#### Scenario: Republish to SoundCloud rebuilds playlist
- **WHEN** a `POST .../publish/soundcloud` request is received for an already-published episode
- **THEN** the system rebuilds the SoundCloud playlist after the metadata update to maintain newest-first ordering

#### Scenario: Episode not in GENERATED status
- **WHEN** a `POST .../publish/soundcloud` request is received for an episode with status `PENDING_REVIEW`
- **THEN** the system returns HTTP 400 indicating the episode must be in GENERATED status

#### Scenario: Episode has no audio file
- **WHEN** a `POST .../publish/soundcloud` request is received for a GENERATED episode where `audioFilePath` is null
- **THEN** the system returns HTTP 400 indicating the episode has no audio file

#### Scenario: Unknown publish target
- **WHEN** a `POST .../publish/youtube` request is received and no publisher is registered for `"youtube"`
- **THEN** the system returns HTTP 400 indicating the target is not supported

#### Scenario: Target not enabled on podcast
- **WHEN** a `POST .../publish/ftp` request is received but the podcast has no enabled `podcast_publication_targets` entry for `"ftp"`
- **THEN** the system returns HTTP 400 indicating the publication target is not configured or not enabled for this podcast

### Requirement: Publish button hidden when fully published
The episode list publish button SHALL only be visible when the episode has NOT been published to all configured publish targets. An episode is considered fully published when it has a publication with status `PUBLISHED` for every target in the TARGETS list.

#### Scenario: Fully published episode hides publish button
- **WHEN** an episode has PUBLISHED publications for all targets (soundcloud and ftp)
- **THEN** the publish (upload) button is not rendered in the episode actions column

#### Scenario: Partially published episode shows publish button
- **WHEN** an episode has PUBLISHED status for soundcloud but no publication for ftp
- **THEN** the publish (upload) button is rendered in the episode actions column

#### Scenario: Episode not found
- **WHEN** a `POST .../publish/soundcloud` request is received for an episode ID that does not exist or does not belong to the specified podcast
- **THEN** the system returns HTTP 404

### Requirement: Same-day publication replacement
When publishing an episode to a target, the system SHALL check for existing publications from other episodes in the same podcast on the same target that share the same `generatedAt` date (YYYY-MM-DD). For each such publication, the system SHALL unpublish the old content from the external platform (e.g. delete the SoundCloud track) and delete the publication record before proceeding with the new publication.

#### Scenario: Publish regenerated episode replaces original
- **WHEN** episode 45 (generatedAt 2026-03-16) is published to SoundCloud, and episode 41 (generatedAt 2026-03-16) is already published to SoundCloud
- **THEN** the system deletes episode 41's SoundCloud track, removes its publication record, and publishes episode 45 as a new track

#### Scenario: Publish episode does not replace different-day episodes
- **WHEN** episode 45 (generatedAt 2026-03-16) is published to SoundCloud, and episode 32 (generatedAt 2026-03-05) is published to SoundCloud
- **THEN** episode 32's publication is not affected

#### Scenario: Unpublish failure does not block new publication
- **WHEN** unpublishing the old track fails (e.g. already deleted on SoundCloud)
- **THEN** the system logs a warning, deletes the old publication record, and proceeds with publishing the new episode

### Requirement: Unpublish support on publisher interface
The `EpisodePublisher` interface SHALL provide an `unpublish(userId, externalId)` method with a default implementation that throws `UnsupportedOperationException`. Publishers that support unpublishing (e.g. SoundCloud) SHALL override this method.

### Requirement: Publication status tracking
The system SHALL store publication records in an `episode_publications` table with columns: `id` (INTEGER, auto-increment PK), `episode_id` (INTEGER, FK to episodes), `target` (TEXT, NOT NULL), `status` (TEXT, NOT NULL — PENDING, PUBLISHED, FAILED, UNPUBLISHED), `external_id` (TEXT, nullable), `external_url` (TEXT, nullable), `error_message` (TEXT, nullable), `published_at` (TEXT, nullable — ISO-8601), `created_at` (TEXT, NOT NULL — ISO-8601). A unique constraint SHALL exist on `(episode_id, target)`.

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

### Requirement: List episode publications endpoint
The system SHALL provide a `GET /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/publications` endpoint that returns all publication records for the specified episode as a JSON array.

#### Scenario: Episode with publications
- **WHEN** a `GET .../publications` request is received for an episode published to SoundCloud
- **THEN** the system returns HTTP 200 with a JSON array containing the publication record (target, status, external_id, external_url, published_at)

#### Scenario: Episode with no publications
- **WHEN** a `GET .../publications` request is received for an episode that has not been published anywhere
- **THEN** the system returns HTTP 200 with an empty JSON array

#### Scenario: Episode not found
- **WHEN** a `GET .../publications` request is received for an episode that does not exist
- **THEN** the system returns HTTP 404

### Requirement: Cascade delete publications with episode
When an episode is deleted (e.g., during cleanup or podcast deletion), all of its publication records SHALL be automatically deleted via `ON DELETE CASCADE` on the `episode_publications.episode_id` foreign key. Application code SHALL NOT manually delete publication records before deleting an episode — the database cascade handles this.

#### Scenario: Episode cleanup removes publications
- **WHEN** an episode with two publication records is deleted by the cleanup scheduler
- **THEN** both publication records are automatically removed from the `episode_publications` table via database cascade

#### Scenario: Podcast deletion cascades to publications
- **WHEN** a podcast is deleted and its episodes have publication records
- **THEN** the episode deletions cascade to remove all associated publication records

### Requirement: Unpublish episode from target endpoint
The system SHALL provide a `DELETE /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/publications/{target}` endpoint. The endpoint SHALL verify the episode has a PUBLISHED publication for the given target, delegate removal to the publisher, update the publication status to `UNPUBLISHED`, clear the `externalId`, and return the updated publication record.

#### Scenario: Successful unpublish from SoundCloud
- **WHEN** a `DELETE .../publications/soundcloud` request is received for an episode with a PUBLISHED SoundCloud publication
- **THEN** the system deletes the track from SoundCloud, rebuilds the playlist, updates the publication status to `UNPUBLISHED`, clears `external_id`, publishes an `episode.unpublished` SSE event, and returns HTTP 200 with the updated publication record

#### Scenario: Successful unpublish from FTP
- **WHEN** a `DELETE .../publications/ftp` request is received for an episode with a PUBLISHED FTP publication
- **THEN** the system deletes the MP3 file from the FTP server, regenerates and uploads feed.xml, updates the publication status to `UNPUBLISHED`, clears `external_id`, publishes an `episode.unpublished` SSE event, and returns HTTP 200

#### Scenario: Episode not published to target
- **WHEN** a `DELETE .../publications/soundcloud` request is received for an episode that is not PUBLISHED to SoundCloud
- **THEN** the system returns HTTP 404

#### Scenario: Unknown target
- **WHEN** a `DELETE .../publications/youtube` request is received for an unsupported target
- **THEN** the system returns HTTP 400

#### Scenario: Episode not found
- **WHEN** a `DELETE .../publications/soundcloud` request is received for an episode that does not exist
- **THEN** the system returns HTTP 404

### Requirement: FTP unpublish implementation
The `FtpPublisher` SHALL implement the `unpublish()` method. It SHALL connect to the FTP server, delete the episode's MP3 file from the remote episodes directory, and regenerate the feed.xml to exclude the unpublished episode.

#### Scenario: FTP file deletion
- **WHEN** `FtpPublisher.unpublish()` is called with a valid externalId
- **THEN** the system connects to the FTP server, deletes the MP3 file at the episode's remote path, and regenerates feed.xml

#### Scenario: FTP file already deleted
- **WHEN** the MP3 file does not exist on the FTP server
- **THEN** the system logs a warning and continues without error

### Requirement: SoundCloud playlist rebuild on every publish operation
After every SoundCloud publish, republish (metadata update), or unpublish operation, the system SHALL rebuild the SoundCloud playlist. This ensures the playlist always reflects the correct newest-first ordering and excludes deleted tracks.

#### Scenario: Playlist rebuilt after publish
- **WHEN** a new episode is published to SoundCloud
- **THEN** the system rebuilds the playlist with all published tracks in newest-first order

#### Scenario: Playlist rebuilt after republish
- **WHEN** an already-published episode's metadata is updated on SoundCloud
- **THEN** the system rebuilds the playlist with all published tracks in newest-first order

#### Scenario: Playlist rebuilt after unpublish
- **WHEN** a SoundCloud track is unpublished
- **THEN** the system rebuilds the playlist with all remaining published tracks

### Requirement: Paginated podcast-level publications endpoint
A `GET /users/{userId}/podcasts/{podcastId}/publications` endpoint SHALL return the publications across all episodes of the podcast, paginated. It SHALL accept `page` (0-indexed, default 0) and `pageSize` (`[1, 200]`, default 20). The response SHALL be a `PagedResponse<PodcastPublicationRow>` ordered by publication `created_at DESC, id DESC`.

Each row SHALL contain:
- `publication`: the standard `PublicationResponse` (id, episodeId, target, status, externalId, externalUrl, errorMessage, publishedAt, createdAt).
- `episode`: a lightweight reference `{ id, generatedAt, status }` so the UI can render the source episode's date and id without a second fetch.

#### Scenario: Page across a podcast's publications
- **WHEN** a podcast has 96 publication rows across its episodes and the client requests page 0 size 20
- **THEN** the response contains 20 items, `total=96`, `totalPages=5`, each item with both `publication` and `episode` populated

#### Scenario: Empty podcast
- **WHEN** the podcast has no episodes (or no publications)
- **THEN** the response is `{ "items": [], "page": 0, "pageSize": 20, "total": 0, "totalPages": 0 }`

#### Scenario: Pagination bounds rejected
- **WHEN** `?page=-1` or `?pageSize=300` is requested
- **THEN** the request SHALL be rejected with HTTP 400

### Requirement: Publications tab uses paginated endpoint
The Publications tab on the podcast detail page SHALL fetch publications via the new podcast-level endpoint, NOT via per-episode fan-out. It SHALL render a paginator below the table (page size 10/20/50/100, default 20). The tab SHALL NOT include a status filter (multi-select filtering is scoped to Episodes only).

#### Scenario: Tab fetches a single paginated request
- **WHEN** the user opens the Publications tab
- **THEN** a single request `GET .../publications?page=0&pageSize=20` is issued (not N per-episode requests)

#### Scenario: Single-episode reuse
- **WHEN** the same `PublicationsTab` component is rendered on the episode detail page with an `episodeId` prop
- **THEN** it falls back to the existing per-episode endpoint and does not render the paginator
