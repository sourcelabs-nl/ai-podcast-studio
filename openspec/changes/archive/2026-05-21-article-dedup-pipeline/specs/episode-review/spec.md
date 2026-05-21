## MODIFIED Requirements

### Requirement: Discard episode script
The system SHALL allow discarding an episode script that is in `PENDING_REVIEW` or `GENERATED` status. Episodes that are published to any target SHALL NOT be discardable. When an episode is discarded, the service SHALL publish an `episode.discarded` event. The system SHALL handle linked articles differently based on whether they are aggregated AND whether they are linked to published episodes:

- **Articles linked to a GENERATED episode with PUBLISHED publications**: The system SHALL NOT reset `isProcessed` and SHALL NOT delete the article, regardless of aggregation status. These articles are considered "final."
- **Non-aggregated articles** (0 or 1 linked posts in `post_articles`) not linked to any published episode: The system SHALL reset the article's `is_processed` flag to `false`, preserving the article's score and summary for reuse.
- **Aggregated articles** (2+ linked posts in `post_articles`) not linked to any published episode: The system SHALL delete all `post_articles` entries for the article, then delete the article itself. This makes the original posts unlinked and eligible for re-aggregation on the next pipeline run.

The system SHALL delegate the "is this article linked to a published episode" check to `ArticleEligibilityService`.

The system SHALL look up linked articles via the `episode_articles` table. If no episode-article links exist (for episodes created before the tracking feature was added), the system SHALL log a warning indicating that no articles could be reset.

#### Scenario: Discard pending episode with non-aggregated articles
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/discard` request is received, the episode status is `PENDING_REVIEW`, and the episode has 3 linked articles that each have 0 or 1 `post_articles` entries and are not linked to any published episode
- **THEN** the system updates the episode status to `DISCARDED`, resets all 3 articles to `is_processed = false`, and returns HTTP 200

#### Scenario: Discard pending episode with aggregated articles
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/discard` request is received, the episode status is `PENDING_REVIEW`, and the episode has 1 linked article that has 5 `post_articles` entries and is not linked to any published episode
- **THEN** the system updates the episode status to `DISCARDED`, deletes all 5 `post_articles` entries for that article, deletes the article, and returns HTTP 200

#### Scenario: Discard pending episode with mixed article types
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/discard` request is received, the episode status is `PENDING_REVIEW`, and the episode has 2 non-aggregated articles and 1 aggregated article (with 4 `post_articles` entries), none linked to published episodes
- **THEN** the system updates the episode status to `DISCARDED`, resets the 2 non-aggregated articles to `is_processed = false`, deletes the 4 `post_articles` entries and the aggregated article, and returns HTTP 200

#### Scenario: Discard episode with articles linked to published episode
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/discard` request is received, and 2 of its 3 linked articles are also linked to a GENERATED episode with PUBLISHED publications
- **THEN** the system updates the episode status to `DISCARDED`, only resets the 1 article not linked to a published episode, and leaves the other 2 articles unchanged

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
