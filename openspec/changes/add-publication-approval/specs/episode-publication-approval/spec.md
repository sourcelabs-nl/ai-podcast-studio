## ADDED Requirements

### Requirement: Per-podcast publication approval flag
The system SHALL provide a per-podcast boolean `requirePublishApproval` (persisted as `podcasts.require_publish_approval`, default `false`) that, when enabled, requires an episode to be explicitly approved for publication before it can be published. The flag SHALL be accepted on podcast create and update and returned in the podcast response, mirroring the existing `requireReview` flag.

#### Scenario: Flag defaults to disabled
- **WHEN** a podcast is created without specifying `requirePublishApproval`
- **THEN** the podcast has `requirePublishApproval = false` and episodes publish without an approval step

#### Scenario: Flag round-trips through the API
- **WHEN** a podcast is updated with `requirePublishApproval = true`
- **THEN** a subsequent fetch of the podcast returns `requirePublishApproval = true`

### Requirement: Per-episode publication approval state
The system SHALL track a per-episode boolean `publishApproved` (persisted as `episodes.publish_approved`, default `1`). When an episode is created or finalized, `publishApproved` SHALL be set to the negation of the podcast's `requirePublishApproval`: episodes for podcasts that do not require approval are created already approved, and episodes for podcasts that require approval are created un-approved. Episodes that existed before this capability SHALL default to approved so they remain publishable.

#### Scenario: Episode created un-approved when approval required
- **WHEN** an episode is generated for a podcast with `requirePublishApproval = true`
- **THEN** the episode is persisted with `publishApproved = false`

#### Scenario: Episode created approved when approval not required
- **WHEN** an episode is generated for a podcast with `requirePublishApproval = false`
- **THEN** the episode is persisted with `publishApproved = true`

#### Scenario: Episode response exposes approval state
- **WHEN** an episode is fetched via the API
- **THEN** the response includes the `publishApproved` boolean

### Requirement: Approve an episode for publication
The system SHALL provide `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/approve-publication` that sets `publishApproved = true` on a `GENERATED` episode, emits an `episode.publication_approved` event, and returns the updated episode. The endpoint SHALL enforce user ownership and SHALL reject episodes that are not in `GENERATED` status.

#### Scenario: Approve a generated episode
- **WHEN** approve-publication is called for a `GENERATED` episode owned by the user
- **THEN** the episode's `publishApproved` becomes `true` and the endpoint returns 200 with the updated episode

#### Scenario: Reject approval for a non-generated episode
- **WHEN** approve-publication is called for an episode that is not `GENERATED` (e.g. `PENDING_REVIEW`)
- **THEN** the endpoint returns 409 and the episode is unchanged

#### Scenario: Reject approval for another user's podcast
- **WHEN** approve-publication is called with a `userId` that does not own the podcast
- **THEN** the endpoint returns 404

### Requirement: Publish gated by approval
When a podcast has `requirePublishApproval = true`, the system SHALL block publishing of an episode whose `publishApproved` is `false`. The guard SHALL apply after the existing `GENERATED`-status check in `PublishingService.publish` and SHALL surface as a 409 response with code `approval_required`. When `requirePublishApproval` is `false`, the `publishApproved` value SHALL be ignored and publishing proceeds as before.

#### Scenario: Publish blocked until approved
- **WHEN** an un-approved episode of a podcast requiring approval is published
- **THEN** the publish is rejected with a 409 `approval_required` response and no publication is created

#### Scenario: Publish allowed after approval
- **WHEN** the same episode is approved for publication and then published
- **THEN** the publish proceeds and the publication is recorded as `PUBLISHED`

#### Scenario: Approval ignored when not required
- **WHEN** an episode with `publishApproved = false` belongs to a podcast with `requirePublishApproval = false` and is published
- **THEN** the publish proceeds normally
