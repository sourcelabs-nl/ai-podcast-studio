## Purpose

Defines the backend API endpoints for fetching upcoming articles and generating a dry-run script preview.

## Requirements

### Requirement: Upcoming articles endpoint
The system SHALL provide `GET /users/{userId}/podcasts/{podcastId}/upcoming-articles` returning the unprocessed articles that would feed the next episode, together with their sources and counts.

Each article SHALL include `postCount`, the number of posts it was aggregated from, matching the episode articles endpoint so both views can show a thread's size. An article that is not an aggregate SHALL report a `postCount` of 1.

#### Scenario: Upcoming articles returned with sources
- **WHEN** a request is made for a podcast with unprocessed articles
- **THEN** the response lists the articles with their source details and counts

#### Scenario: Aggregated upcoming article reports its thread size
- **WHEN** an upcoming article was aggregated from 8 posts
- **THEN** its `postCount` is 8

### Requirement: Upcoming articles response includes effective article count
The `GET /users/{userId}/podcasts/{podcastId}/upcoming-articles` endpoint SHALL return `articleCount` as the effective number of articles after pre-calculating post-to-article aggregation. Unlinked posts from sources where `shouldAggregate()` returns true and with more than one post SHALL be counted as a single article per source.

#### Scenario: Aggregatable source posts counted as one article
- **WHEN** a podcast has 10 already-aggregated articles and 20 unlinked posts from 2 aggregatable twitter sources (12 from source A, 8 from source B)
- **THEN** `articleCount` is 12 (10 existing + 1 for source A + 1 for source B)

#### Scenario: Non-aggregatable source posts counted individually
- **WHEN** a podcast has 5 already-aggregated articles and 3 unlinked posts from a non-aggregatable RSS source
- **THEN** `articleCount` is 8 (5 existing + 3 individual posts)

#### Scenario: Single unlinked post from aggregatable source counted individually
- **WHEN** a podcast has 1 unlinked post from an aggregatable twitter source
- **THEN** `articleCount` counts that post as 1 (aggregation only groups when >1 post)

### Requirement: Preview script endpoint
The system SHALL provide a `POST /users/{userId}/podcasts/{podcastId}/preview` endpoint that performs a dry-run: scores any unscored articles, composes a script from relevant unprocessed articles, but does NOT create an episode and does NOT mark articles as processed.

#### Scenario: Successful preview
- **WHEN** a POST request is made and there are relevant articles
- **THEN** the response SHALL return the composed scriptText, the podcast style, and the list of articles used

#### Scenario: No articles for preview
- **WHEN** a POST request is made but there are no relevant unprocessed articles
- **THEN** the response SHALL return 200 with an appropriate message indicating no content is available

#### Scenario: Scoring side effect
- **WHEN** a preview is requested and there are unscored articles
- **THEN** those articles SHALL be scored and their scores persisted, but articles SHALL NOT be marked as processed
