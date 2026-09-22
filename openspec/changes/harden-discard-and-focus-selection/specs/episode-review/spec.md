## MODIFIED Requirements

### Requirement: Discard episode script
The system SHALL allow discarding an episode that is in `PENDING_REVIEW`, `GENERATED` or `FAILED` status. Episodes in `GENERATING_AUDIO` status SHALL NOT be discardable. Episodes with any publication in `PUBLISHED` status SHALL NOT be discardable, whatever their status: the system SHALL return HTTP 409 with the message "Episode is published to <targets>; unpublish it first", naming each published target, and SHALL leave the episode and its articles unchanged. The check SHALL live in the service layer, so every discard path enforces it. When an episode is discarded, the service SHALL publish an `episode.discarded` event. The system SHALL handle linked articles differently based on whether they are aggregated:

- **Non-aggregated articles** (0 or 1 linked posts in `post_articles`): The system SHALL reset the article's `is_processed` flag to `false`, preserving the article's score and summary for reuse.
- **Aggregated articles** (2+ linked posts in `post_articles`): The system SHALL delete all `post_articles` entries for the article, then delete the article itself. This makes the original posts unlinked and eligible for re-aggregation on the next pipeline run.

The system SHALL look up linked articles via the `episode_articles` table. If no episode-article links exist (for episodes created before the tracking feature was added), the system SHALL log a warning indicating that no articles could be reset. A `FAILED` episode is discarded without resetting its articles.

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
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/discard` request is received and the episode status is not `PENDING_REVIEW`, `GENERATED` or `FAILED`
- **THEN** the system returns HTTP 409 (Conflict) with an error message

#### Scenario: Discard GENERATING_AUDIO episode blocked
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/discard` request is received and the episode status is `GENERATING_AUDIO`
- **THEN** the system returns HTTP 409 (Conflict) with an error message indicating audio generation is in progress

#### Scenario: Discard published episode blocked
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/discard` request is received and the episode has a PUBLISHED publication to `ftp`
- **THEN** the system returns HTTP 409 (Conflict) with the error "Episode is published to ftp; unpublish it first", and the episode keeps its status and its publication

#### Scenario: Unpublished or failed publications do not block discard
- **WHEN** a discard request is received for an episode whose publications are all `UNPUBLISHED` or `FAILED`
- **THEN** the episode is discarded as usual
