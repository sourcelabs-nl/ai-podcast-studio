# Capability: Podcast Sources

## Purpose

CRUD operations for content sources scoped to podcasts, database-driven source polling (replacing YAML config), and source-scoped article deduplication.

## Requirements

### Requirement: Add source for a podcast
The system SHALL allow adding a content source scoped to a specific podcast. Each source record SHALL have: `id` (TEXT, primary key), `podcast_id` (TEXT, FK to podcasts), `type` (TEXT, one of: rss, website, twitter), `url` (TEXT), `poll_interval_minutes` (INTEGER, default 60), `enabled` (INTEGER, default 1), `last_polled` (TEXT, nullable), `last_seen_id` (TEXT, nullable). The create request SHALL accept an optional `label` (string, nullable) field for a human-readable display name.

#### Scenario: Add an RSS source to a podcast
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/sources` request is received with `type: "rss"`, `url`, and optionally `pollIntervalMinutes`, `enabled`, and `label`
- **THEN** the system creates a source record linked to the podcast (with label if provided) and returns HTTP 201 with the created source

#### Scenario: Add a Twitter source to a podcast
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/sources` request is received with `type: "twitter"` and `url` set to an X username or full X URL
- **THEN** the system creates a source record linked to the podcast and returns HTTP 201 with the created source

#### Scenario: Add source for non-existing podcast
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/sources` request is received for a podcast that does not exist
- **THEN** the system returns HTTP 404

#### Scenario: Add source with missing required fields
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/sources` request is received without `type` or `url`
- **THEN** the system returns HTTP 400 with a validation error message

### Requirement: List sources for a podcast
The system SHALL provide an endpoint to retrieve the sources belonging to a specific podcast.

The endpoint SHALL accept an optional `enabled` query parameter. When `true` only enabled sources SHALL be returned, when `false` only disabled ones, and when the parameter is omitted all sources SHALL be returned so existing callers are unaffected.

The filter SHALL be applied in the repository query rather than by discarding rows after loading them, so that the per-source article, post and host-breaker lookups performed for the response only run for the sources being returned. A podcast accumulates disabled sources it cannot delete — a retired source's posts and articles are removed with it and `episode_articles` cascades — so the disabled set grows without bound and is worth excluding at the query.

#### Scenario: List podcast's sources
- **WHEN** a `GET /users/{userId}/podcasts/{podcastId}/sources` request is received for an existing podcast
- **THEN** the system returns HTTP 200 with a JSON array of all the podcast's sources, enabled and disabled

#### Scenario: Only enabled sources
- **WHEN** the request carries `?enabled=true`
- **THEN** only sources whose `enabled` is true are returned

#### Scenario: Only disabled sources
- **WHEN** the request carries `?enabled=false`
- **THEN** only sources whose `enabled` is false are returned

#### Scenario: Counts reflect the filtered set
- **WHEN** the request carries `?enabled=true` and the podcast has 24 enabled and 37 disabled sources
- **THEN** 24 sources are returned, each with its article, post and breaker details

#### Scenario: List sources for non-existing podcast
- **WHEN** a `GET /users/{userId}/podcasts/{podcastId}/sources` request is received for a podcast that does not exist
- **THEN** the system returns HTTP 404

### Requirement: Update a podcast's source
The system SHALL allow updating a source's `url`, `type`, `pollIntervalMinutes`, `enabled`, and `label` fields.

#### Scenario: Update source configuration
- **WHEN** a `PUT /users/{userId}/podcasts/{podcastId}/sources/{sourceId}` request is received with updated fields (including optional `label`)
- **THEN** the system updates the source record and returns HTTP 200 with the updated source

#### Scenario: Update source belonging to a different podcast
- **WHEN** a `PUT /users/{userId}/podcasts/{podcastId}/sources/{sourceId}` request is received but the source belongs to a different podcast
- **THEN** the system returns HTTP 404

### Requirement: Delete a podcast's source
The system SHALL allow deleting a source, which MUST also delete all articles associated with that source.

#### Scenario: Delete existing source
- **WHEN** a `DELETE /users/{userId}/podcasts/{podcastId}/sources/{sourceId}` request is received for an existing source owned by the podcast
- **THEN** the system deletes the source and all its articles, and returns HTTP 204

#### Scenario: Delete non-existing source
- **WHEN** a `DELETE /users/{userId}/podcasts/{podcastId}/sources/{sourceId}` request is received for a source that does not exist or belongs to a different podcast
- **THEN** the system returns HTTP 404

### Requirement: Database-driven source polling replaces YAML config
The system SHALL poll sources from the database instead of from YAML configuration. The `SourceProperties` config class and `app.sources` YAML block SHALL be removed. The `SourcePollingScheduler` SHALL query all enabled sources from the database and poll each one based on its `poll_interval_minutes`.

#### Scenario: Poll sources from database
- **WHEN** the polling scheduler runs
- **THEN** it queries all sources from the database where `enabled = 1`, checks each source's `last_polled` against its `poll_interval_minutes`, and polls sources that are due

#### Scenario: No sources configured
- **WHEN** the polling scheduler runs and no sources exist in the database
- **THEN** it completes without polling anything

### Requirement: Article deduplication scoped to source
The `content_hash` uniqueness constraint SHALL be `UNIQUE(source_id, content_hash)` instead of globally unique. This allows the same article content to exist under different podcasts' sources while preventing duplicates within a single source.

#### Scenario: Same article from same source
- **WHEN** a source is polled and an article with the same `content_hash` already exists for that source
- **THEN** the article is skipped (not saved again)

#### Scenario: Same article from different podcasts' sources
- **WHEN** two podcasts subscribe to the same RSS feed and both sources are polled
- **THEN** the article is saved separately for each podcast's source (different `source_id`, same `content_hash`)
