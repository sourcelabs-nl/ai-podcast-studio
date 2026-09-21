## MODIFIED Requirements

### Requirement: SoundCloud track upload
The `SoundCloudPublisher` SHALL implement the `EpisodePublisher` interface. It SHALL upload the episode's MP3 file to SoundCloud via `POST https://api.soundcloud.com/tracks` with `multipart/form-data` containing: `track[title]` (podcast name + episode date), `track[description]` (the episode's `recap` field if available, falling back to the first 500 characters of script text), `track[tag_list]` (derived from podcast topic, space-separated, multi-word tags quoted), `track[sharing]` set to `"public"`, `track[permalink]` (URL-safe slug derived from podcast name and episode date), and `track[asset_data]` (the MP3 file). The permalink slug SHALL be computed by concatenating the podcast name and date with a hyphen, converting to lowercase, replacing non-alphanumeric characters (except hyphens) with hyphens, collapsing consecutive hyphens, and trimming leading/trailing hyphens. The upload SHALL use the user's decrypted OAuth access token as a Bearer token. The publisher SHALL NOT manage playlist membership directly; playlist ordering is handled exclusively by the playlist rebuild in `PublishingService`.

The client SHALL translate a `403` response whose body reports that a subscription is required into a `SoundCloudUploadNotPermittedException`. That exception SHALL NOT extend `HttpClientErrorException`, so the publisher cannot mistake it for a condition that deleting tracks would relieve. Its message SHALL carry SoundCloud's own `message` field rather than the raw error envelope, falling back to the raw body when that field cannot be read; the full body SHALL be logged either way. The publishing exception handler SHALL report it as HTTP 403 with `code: "upload_not_permitted"`, because a plan that bars API uploads is the user's to resolve and not a server fault. SoundCloud began returning this response on 11 September 2026 to an account holding six tracks with 1610 seconds of upload quota free, hours after the same call had succeeded, so it is a property of the account's plan rather than of the request or of how full the account is.

#### Scenario: Successful upload with permalink
- **WHEN** the publisher uploads an episode for a podcast named "Tech News" generated on 2026-02-13
- **THEN** the SoundCloud track is created with title "Tech News - 2026-02-13", permalink `"tech-news-2026-02-13"`, description from the script, tags from the topic, and the system returns a `PublishResult` with the SoundCloud track ID and permalink URL

#### Scenario: Upload fails due to SoundCloud API error
- **WHEN** the upload request returns a non-2xx response from SoundCloud
- **THEN** the publisher throws an exception with the SoundCloud error message

#### Scenario: Upload refused by the account's plan
- **WHEN** `POST /tracks` returns `403` with a body reporting that an active subscription is required
- **THEN** the client throws `SoundCloudUploadNotPermittedException`, and no track is deleted

#### Scenario: Refusal reported as prose
- **WHEN** the refusal reaches the publish endpoint
- **THEN** the response is HTTP 403 with `code: "upload_not_permitted"` and an `error` reading "SoundCloud refused the upload: An active Pro Unlimited subscription is required for this action", not the raw JSON envelope

#### Scenario: No OAuth connection for user
- **WHEN** the publisher is called for a user with no SoundCloud `oauth_connections` record
- **THEN** the publisher throws an exception indicating the user must connect their SoundCloud account first

### Requirement: SoundCloud upload quota check
The system SHALL ensure enough SoundCloud upload quota exists for an episode automatically, server-side, without user consent. Freeing quota deletes published episodes permanently, so it SHALL run only after an upload attempt has actually been refused, never on a prediction that one will be. The system SHALL therefore attempt the upload first.

When an upload fails with an `HttpClientErrorException`, the system SHALL call `GET https://api.soundcloud.com/me` once and read the `quota` object. If `quota.unlimitedUploadQuota` is `false` and the remaining quota is too small for the episode (`quota.uploadSecondsLeft < episode.durationSeconds`, or `uploadSecondsLeft <= 0` when the duration is unknown), the system SHALL free space by deleting the **oldest** tracks belonging to this podcast. The system SHALL compute `secondsToFree = requiredSeconds - uploadSecondsLeft + buffer` (buffer = 120s), fetch tracks via `GET https://api.soundcloud.com/me/tracks`, filter to tracks whose title starts with the podcast name, sort oldest-first by `createdAt`, and delete them one at a time, accumulating each track's duration, until at least `secondsToFree` has been freed. The system SHALL NOT re-read the quota after deleting; the deletion amount is sized from the accurate pre-deletion read. After deleting at least one track, the system SHALL wait ~4 seconds (using a coroutine `delay`) so SoundCloud registers the freed space, then retry the upload once.

Freeing SHALL report whether it deleted anything. When nothing was deleted — the account is unlimited, the quota was already sufficient, or no deletable tracks exist — the system SHALL NOT retry, and SHALL propagate the original failure, because a second attempt has no more room than the first.

A `SoundCloudUploadNotPermittedException` SHALL propagate without any quota read, deletion, or retry.

#### Scenario: Quota available
- **WHEN** the upload succeeds on the first attempt
- **THEN** no quota is read, nothing is deleted, and there is no wait

#### Scenario: Quota exceeded — automatic freeing
- **WHEN** the first upload attempt is refused and the remaining quota is too small for the episode with `unlimitedUploadQuota` false
- **THEN** the system deletes the oldest podcast tracks (oldest-first) until at least `secondsToFree` is freed, waits ~4s, and retries the upload once

#### Scenario: Oldest-first, just-enough deletion
- **WHEN** the account has podcast tracks A (oldest), B, C and only A+B are needed to cover `secondsToFree`
- **THEN** the system deletes A and B, keeps C and any tracks belonging to other podcasts, and does not delete more than required

#### Scenario: No deletable tracks
- **WHEN** the upload is refused, the quota is exceeded, and no tracks whose title starts with the podcast name exist
- **THEN** the system logs a warning, deletes nothing, does not retry, and propagates the failure

#### Scenario: Account may not upload at all
- **WHEN** the upload is refused with `SoundCloudUploadNotPermittedException`
- **THEN** the system deletes nothing, does not read the track list, and propagates the exception

#### Scenario: Unlimited quota
- **WHEN** the user has `unlimitedUploadQuota: true` and an upload is refused
- **THEN** the system deletes nothing and propagates the failure

## ADDED Requirements

### Requirement: SoundCloud audio replacement ordering
SoundCloud offers no way to swap a track's audio in place, so the publisher SHALL replace a published episode by uploading its new audio and deleting the track it replaces.

The replacement SHALL be uploaded **before** the old track is deleted. A refused upload then leaves the episode published rather than removing it and putting nothing back. The old track holds the episode's canonical permalink for as long as it exists, so SoundCloud assigns the replacement a suffixed variant; once the old track is deleted the system SHALL claim the canonical slug on the replacement via `PUT /tracks/{id}` and SHALL return that reclaimed URL as the publication's `externalUrl`. After deleting, the system SHALL wait ~4 seconds so SoundCloud registers the returned quota before any following publish reads it.

A republish SHALL be idempotent: `deleteTrack` treats an already-deleted track as done, so an attempt that uploaded the replacement and then failed can simply be retried. The operation SHALL return the new track id; the one passed in is dead once it returns.

#### Scenario: Replacement uploaded before the old track is deleted
- **WHEN** an episode published as track 999 is republished
- **THEN** the new audio is uploaded first, then track 999 is deleted, then the replacement claims the canonical permalink

#### Scenario: Refused replacement leaves the episode published
- **WHEN** the replacement upload is refused
- **THEN** no track is deleted and the previously published track stays live

#### Scenario: Replacement keeps the canonical URL
- **WHEN** a replacement is uploaded for a podcast named "Tech News" generated on 2026-02-13
- **THEN** the replacement ends up on permalink `"tech-news-2026-02-13"` and the returned `externalUrl` is that canonical URL
