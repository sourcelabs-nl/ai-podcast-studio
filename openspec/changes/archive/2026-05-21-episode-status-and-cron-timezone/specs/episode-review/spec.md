## MODIFIED Requirements

### Requirement: Episode status lifecycle
Each episode SHALL have a `status` field with one of the following values: `PENDING_REVIEW`, `APPROVED`, `GENERATING_AUDIO`, `GENERATED`, `FAILED`, `DISCARDED`. The status determines where the episode is in the review-to-audio pipeline. `GENERATING_AUDIO` indicates that TTS audio generation is actively in progress. An episode can enter `FAILED` status either from a TTS generation failure (after approval) or from a pipeline generation error (e.g., invalid model configuration). In the pipeline error case, a FAILED episode is created with an empty `scriptText`, the error stored in `errorMessage`, and `lastGeneratedAt` updated to prevent scheduler retries. After each status transition, the service SHALL publish a `PodcastEvent` via `ApplicationEventPublisher` to notify connected clients.

#### Scenario: New episode created with review enabled
- **WHEN** the pipeline generates a script for a podcast with `requireReview = true`
- **THEN** an episode is created with status `PENDING_REVIEW`, the `scriptText` populated, `audioFilePath` and `durationSeconds` set to null, and an `episode.created` event is published

#### Scenario: New episode created without review
- **WHEN** the pipeline generates a script for a podcast with `requireReview = false`
- **THEN** the episode is created with status `GENERATED` after TTS completes, with all fields populated, and an `episode.generated` event is published

### Requirement: Approve episode script
The system SHALL allow approving an episode script, which triggers async TTS generation. Each status transition during the approval and generation flow SHALL publish a corresponding event.

#### Scenario: Approve pending episode
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/approve` request is received and the episode status is `PENDING_REVIEW`
- **THEN** the system updates the episode status to `APPROVED`, publishes an `episode.approved` event, triggers TTS generation asynchronously, and returns HTTP 202 (Accepted)

#### Scenario: Approve non-pending episode
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/approve` request is received and the episode status is not `PENDING_REVIEW`
- **THEN** the system returns HTTP 409 (Conflict) with an error message

#### Scenario: TTS generation starts
- **WHEN** the async TTS pipeline begins generating audio for an approved episode
- **THEN** the episode status is updated to `GENERATING_AUDIO` in the database, and an `episode.audio.started` event is published

#### Scenario: TTS generation succeeds after approval
- **WHEN** the async TTS pipeline completes successfully for an episode in `GENERATING_AUDIO` status
- **THEN** the episode status is updated to `GENERATED`, `audioFilePath` and `durationSeconds` are populated, and an `episode.generated` event is published

#### Scenario: TTS generation fails after approval
- **WHEN** the async TTS pipeline fails for an episode in `GENERATING_AUDIO` status
- **THEN** the episode status is updated to `FAILED` and an `episode.failed` event is published

### Requirement: Retry failed episode
The system SHALL allow re-triggering TTS for a `FAILED` episode by approving it again.

#### Scenario: Approve failed episode
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/approve` request is received and the episode status is `FAILED`
- **THEN** the system updates the episode status to `APPROVED`, publishes an `episode.approved` event, triggers TTS generation asynchronously, and returns HTTP 202

### Requirement: Discard episode script
The system SHALL allow discarding an episode script that is in `PENDING_REVIEW` or `GENERATED` status. Episodes in `GENERATING_AUDIO` status SHALL NOT be discardable. Episodes that are published to any target SHALL NOT be discardable. When an episode is discarded, the service SHALL publish an `episode.discarded` event. The system SHALL handle linked articles differently based on whether they are aggregated:

- **Non-aggregated articles** (0 or 1 linked posts in `post_articles`): The system SHALL reset the article's `is_processed` flag to `false`, preserving the article's score and summary for reuse.
- **Aggregated articles** (2+ linked posts in `post_articles`): The system SHALL delete all `post_articles` entries for the article, then delete the article itself. This makes the original posts unlinked and eligible for re-aggregation on the next pipeline run.

The system SHALL look up linked articles via the `episode_articles` table. If no episode-article links exist (for episodes created before the tracking feature was added), the system SHALL log a warning indicating that no articles could be reset.

#### Scenario: Discard pending episode with non-aggregated articles
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/discard` request is received, the episode status is `PENDING_REVIEW`, and the episode has 3 linked articles that each have 0 or 1 `post_articles` entries
- **THEN** the system updates the episode status to `DISCARDED`, resets all 3 articles to `is_processed = false`, and returns HTTP 200

#### Scenario: Discard pending episode with aggregated articles
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/discard` request is received, the episode status is `PENDING_REVIEW`, and the episode has 1 linked article that has 5 `post_articles` entries
- **THEN** the system updates the episode status to `DISCARDED`, deletes all 5 `post_articles` entries for that article, deletes the article, and returns HTTP 200

#### Scenario: Discard pending episode with mixed article types
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/discard` request is received, the episode status is `PENDING_REVIEW`, and the episode has 2 non-aggregated articles and 1 aggregated article (with 4 `post_articles` entries)
- **THEN** the system updates the episode status to `DISCARDED`, resets the 2 non-aggregated articles to `is_processed = false`, deletes the 4 `post_articles` entries and the aggregated article, and returns HTTP 200

#### Scenario: Discard pending episode without article links
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/discard` request is received, the episode status is `PENDING_REVIEW`, and the episode has no linked articles in `episode_articles`
- **THEN** the system updates the episode status to `DISCARDED`, logs a warning that no articles could be reset, and returns HTTP 200

#### Scenario: Discard generated episode
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/discard` request is received and the episode status is `GENERATED` and the episode has no PUBLISHED publications
- **THEN** the system updates the episode status to `DISCARDED`, handles linked articles as above, and returns HTTP 200

#### Scenario: Discard non-discardable episode
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/discard` request is received and the episode status is not `PENDING_REVIEW` or `GENERATED`
- **THEN** the system returns HTTP 409 (Conflict) with an error message

#### Scenario: Discard published episode blocked
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/discard` request is received and the episode has a PUBLISHED publication to any target
- **THEN** the system returns HTTP 409 (Conflict) indicating the episode must be unpublished first

#### Scenario: Discard GENERATING_AUDIO episode blocked
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/discard` request is received and the episode status is `GENERATING_AUDIO`
- **THEN** the system returns HTTP 409 (Conflict) with an error message indicating audio generation is in progress