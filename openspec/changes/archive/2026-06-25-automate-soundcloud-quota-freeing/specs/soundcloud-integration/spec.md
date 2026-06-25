## MODIFIED Requirements

### Requirement: SoundCloud upload quota check
The system SHALL ensure enough SoundCloud upload quota exists for an episode automatically, server-side, before uploading — without user consent. Before uploading, the system SHALL call `GET https://api.soundcloud.com/me` once and read the `quota` object. If `quota.unlimitedUploadQuota` is `false` and the remaining quota is too small for the episode (`quota.uploadSecondsLeft < episode.durationSeconds`, or `uploadSecondsLeft <= 0` when the duration is unknown), the system SHALL free space by deleting the **oldest** tracks belonging to this podcast. The system SHALL compute `secondsToFree = requiredSeconds - uploadSecondsLeft + buffer` (buffer = 120s), fetch tracks via `GET https://api.soundcloud.com/me/tracks`, filter to tracks whose title starts with the podcast name, sort oldest-first by `createdAt`, and delete them one at a time, accumulating each track's duration, until at least `secondsToFree` has been freed. The system SHALL NOT re-read the quota after deleting; the deletion amount is sized from the accurate pre-deletion read. After deleting at least one track, the system SHALL wait ~4 seconds (using a coroutine `delay`) so SoundCloud registers the freed space, then upload. If the quota is already sufficient, the account is unlimited, or no deletable tracks exist, the system SHALL proceed directly to upload.

#### Scenario: Quota available
- **WHEN** the remaining quota fits the episode duration (or the account is unlimited)
- **THEN** the publish flow uploads directly with no deletion or wait

#### Scenario: Quota exceeded — automatic freeing
- **WHEN** the remaining quota is too small for the episode and `unlimitedUploadQuota` is `false`
- **THEN** the system deletes the oldest podcast tracks (oldest-first) until at least `secondsToFree` is freed, waits ~4s, and then uploads — returning HTTP 200 with the published publication on success

#### Scenario: Oldest-first, just-enough deletion
- **WHEN** the account has podcast tracks A (oldest), B, C and only A+B are needed to cover `secondsToFree`
- **THEN** the system deletes A and B, keeps C and any tracks belonging to other podcasts, and does not delete more than required

#### Scenario: No deletable tracks
- **WHEN** the quota is exceeded but no tracks whose title starts with the podcast name exist
- **THEN** the system logs a warning and attempts the upload anyway (no deletion, no wait)

#### Scenario: Unlimited quota
- **WHEN** the user has `unlimitedUploadQuota: true`
- **THEN** the publish flow uploads directly regardless of `uploadSecondsLeft`

### Requirement: Frontend re-authorize and quota recovery
The publish wizard SHALL display contextual recovery actions based on the error type. On HTTP 401 with `code: "oauth_expired"`, a "Re-authorize SoundCloud" button SHALL be shown that fetches the authorization URL and opens it in a new tab. Upload-quota shortfalls are handled automatically server-side, so the wizard SHALL NOT present any quota-consent step, track-removal prompt, or HTTP 413 handling.

#### Scenario: OAuth expired error
- **WHEN** publishing fails with HTTP 401
- **THEN** the wizard shows the error message and a "Re-authorize SoundCloud" button with a KeyRound icon

#### Scenario: Quota handled automatically
- **WHEN** the user's upload quota is too small for the episode
- **THEN** the server frees space and publishes automatically, and the wizard shows a normal success result with no track-removal prompt
