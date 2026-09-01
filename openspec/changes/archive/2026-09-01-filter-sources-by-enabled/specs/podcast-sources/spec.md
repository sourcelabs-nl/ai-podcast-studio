## MODIFIED Requirements

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
