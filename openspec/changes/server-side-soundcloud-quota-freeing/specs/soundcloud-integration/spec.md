## MODIFIED Requirements

### Requirement: SoundCloud upload quota check
The system SHALL check the user's SoundCloud upload quota before attempting a track upload by calling `GET https://api.soundcloud.com/me` and reading the `quota` object. When `quota.unlimitedUploadQuota` is `false`, the system SHALL determine whether the episode fits: if the episode's `durationSeconds` is known, the quota is exceeded when `uploadSecondsLeft < durationSeconds`; if it is unknown, the quota is exceeded when `uploadSecondsLeft <= 0`. When exceeded, the system SHALL throw a `SoundCloudQuotaExceededException` carrying a `QuotaDeletionPlan`: the number of seconds that must be freed (`durationSeconds - uploadSecondsLeft` plus a fixed safety buffer) and the ordered list of oldest tracks to delete to free that much. Candidate tracks are obtained from `GET https://api.soundcloud.com/me/tracks`, filtered to titles starting with the podcast name, sorted oldest-first by `createdAt`, and accumulated (each track's `duration` converted from milliseconds to seconds) until the freed total meets the requirement.

#### Scenario: Quota fits the episode
- **WHEN** `uploadSecondsLeft` is greater than or equal to the episode's `durationSeconds`
- **THEN** the publish flow proceeds normally

#### Scenario: Quota too small for the episode
- **WHEN** `uploadSecondsLeft` is less than the episode's `durationSeconds` and `unlimitedUploadQuota` is `false`
- **THEN** the system returns HTTP 413 with `code: "quota_exceeded"`, `secondsToFree`, and `tracksToDelete[]` (each with id, title, createdAt, durationSeconds) listing enough oldest podcast tracks to free the required space

#### Scenario: Unlimited quota
- **WHEN** the user has `unlimitedUploadQuota: true`
- **THEN** the publish flow proceeds normally regardless of `uploadSecondsLeft`

#### Scenario: Deletion plan filtering
- **WHEN** the SoundCloud account has tracks from multiple sources
- **THEN** only tracks whose title starts with the podcast name are eligible for the deletion plan, selected oldest-first until enough quota is freed

### Requirement: Frontend re-authorize and quota recovery
The publish wizard SHALL display contextual recovery actions based on the error type. On HTTP 401 with `code: "oauth_expired"`, a "Re-authorize SoundCloud" button SHALL be shown that fetches the authorization URL and opens it in a new tab. On HTTP 413 with `code: "quota_exceeded"`, the wizard SHALL display the server-computed list of tracks to delete and a single "Remove & publish" action. The wizard SHALL NOT compute which or how many tracks to delete, delete tracks individually, or retry publishing on a timer; it forwards the consented track IDs to the server, which performs the deletion and publish in one operation.

#### Scenario: OAuth expired error
- **WHEN** publishing fails with HTTP 401
- **THEN** the wizard shows the error message and a "Re-authorize SoundCloud" button with a KeyRound icon

#### Scenario: Quota exceeded shows the deletion plan
- **WHEN** publishing fails with HTTP 413 and a `tracksToDelete` list is returned
- **THEN** the wizard shows the error message, the list of track titles, and a destructive "Remove & publish" button

#### Scenario: Consent triggers server-side free-and-publish
- **WHEN** the user clicks "Remove & publish"
- **THEN** the wizard calls `POST .../publish/{target}/free-and-publish` with the planned `trackIds` and shows the published result, or a fresh plan if more space is still required

## ADDED Requirements

### Requirement: Free quota and publish endpoint
The system SHALL provide `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/publish/{target}/free-and-publish` accepting `{ "trackIds": [<long>] }`. It SHALL delete the given SoundCloud tracks using the user's access token and then publish the episode, as a single server operation. The endpoint SHALL enforce user ownership and that the episode belongs to the podcast. It SHALL reject targets other than SoundCloud. If, after deletion, the quota is still insufficient, the re-thrown `SoundCloudQuotaExceededException` SHALL be returned as a fresh 413 plan.

#### Scenario: Delete consented tracks then publish
- **WHEN** the endpoint is called with track IDs the user consented to delete and the freed space is sufficient
- **THEN** the system deletes those tracks, publishes the episode, and returns HTTP 200 with the publication

#### Scenario: Still insufficient after freeing
- **WHEN** deleting the consented tracks does not free enough quota
- **THEN** the system returns HTTP 413 with a fresh `quota_exceeded` plan

#### Scenario: Non-SoundCloud target rejected
- **WHEN** the endpoint is called with a target other than `soundcloud`
- **THEN** the system rejects the request with HTTP 400

### Requirement: Centralized publishing exception translation
Publishing failures SHALL be translated to HTTP responses by a `@RestControllerAdvice` scoped to `PublishingController`, not by inline `try/catch` in the publish endpoints. The advice SHALL map `SoundCloudQuotaExceededException` to HTTP 413 with `code: "quota_exceeded"`, `secondsToFree`, and `tracksToDelete[]`.

#### Scenario: Quota exception mapped by advice
- **WHEN** `PublishingService.publish` or `freeQuotaAndPublish` throws `SoundCloudQuotaExceededException`
- **THEN** the advice returns HTTP 413 with the quota plan body, without the controller catching the exception inline
