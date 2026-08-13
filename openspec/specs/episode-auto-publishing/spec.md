# episode-auto-publishing Specification

## Purpose
Automatic publication of an episode to its enabled targets as soon as generation completes, without interrupting generation when a target fails.
## Requirements
### Requirement: Auto-publish episodes on generation
The system SHALL automatically publish a newly generated episode to every publication target of its podcast that is both `enabled` and has `autoPublish` turned on, when the episode reaches `GENERATED` status. Auto-publish SHALL reuse the existing manual publishing pipeline (`PublishingService.publish`), so same-day replacement, static feed export, and SoundCloud playlist rebuild behave identically to a manual publish.

#### Scenario: Episode auto-published to enabled auto-publish target
- **WHEN** an episode reaches `GENERATED` status for a podcast whose FTP target has `enabled = true` and `autoPublish = true`
- **THEN** the system publishes the episode to FTP using the same pipeline as a manual publish and records a `PUBLISHED` `EpisodePublication`

#### Scenario: Target enabled but auto-publish off is skipped
- **WHEN** an episode reaches `GENERATED` for a podcast whose SoundCloud target has `enabled = true` and `autoPublish = false`
- **THEN** the system does not publish the episode to SoundCloud automatically

#### Scenario: Multiple auto-publish targets published independently
- **WHEN** an episode reaches `GENERATED` for a podcast with both FTP and SoundCloud targets having `autoPublish = true` and one target's publish fails
- **THEN** the system still publishes to the other target, and the failed target is recorded with `PublicationStatus.FAILED`

#### Scenario: No auto-publish targets configured
- **WHEN** an episode reaches `GENERATED` for a podcast with no target having `autoPublish = true`
- **THEN** the system performs no automatic publishing

### Requirement: Auto-publish failures do not interrupt generation
The system SHALL treat auto-publish as a best-effort, non-blocking side effect of episode generation. A failure to publish to a target MUST NOT change the episode's `GENERATED` status, MUST be recorded as a `FAILED` publication, and MUST surface via the existing `episode.publish.failed` event so the user can retry manually.

#### Scenario: Publish failure leaves episode generated
- **WHEN** auto-publishing to a target throws an error
- **THEN** the episode remains in `GENERATED` status, a `FAILED` publication is recorded, and an `episode.publish.failed` event is emitted

