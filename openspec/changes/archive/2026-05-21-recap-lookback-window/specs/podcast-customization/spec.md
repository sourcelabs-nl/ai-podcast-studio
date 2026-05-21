## ADDED Requirements

### Requirement: Recap lookback episodes per podcast
Each podcast SHALL have an optional `recap_lookback_episodes` field (INTEGER, nullable). When set, the LLM pipeline SHALL use this value as the number of recent episode recaps to pass to the composer for topic deduplication. When null, the system SHALL fall back to the global `app.episode.recap-lookback-episodes` config value (default: 7). The field SHALL be accepted in podcast create (`POST`) and update (`PUT`) endpoints and included in GET responses. The field SHALL use a `@JsonProperty` annotation on the DTO to ensure correct deserialization.

#### Scenario: Podcast with custom recap lookback
- **WHEN** a podcast has `recap_lookback_episodes` set to 3
- **THEN** the LLM pipeline fetches the 3 most recent episode recaps for deduplication context

#### Scenario: Podcast with no recap lookback override
- **WHEN** a podcast has `recap_lookback_episodes` set to null
- **THEN** the LLM pipeline uses the global `app.episode.recap-lookback-episodes` value (7)

#### Scenario: Recap lookback accepted in create endpoint
- **WHEN** a `POST /users/{userId}/podcasts` request includes `"recapLookbackEpisodes": 5`
- **THEN** the podcast is created with `recap_lookback_episodes` set to 5

#### Scenario: Recap lookback accepted in update endpoint
- **WHEN** a `PUT /users/{userId}/podcasts/{podcastId}` request includes `"recapLookbackEpisodes": 10`
- **THEN** the podcast's `recap_lookback_episodes` is updated to 10

#### Scenario: Recap lookback included in GET response
- **WHEN** a `GET /users/{userId}/podcasts/{podcastId}` request is received for a podcast with `recap_lookback_episodes` set to 5
- **THEN** the response includes `"recapLookbackEpisodes": 5`

#### Scenario: Clear recap lookback override
- **WHEN** a `PUT /users/{userId}/podcasts/{podcastId}` request includes `"recapLookbackEpisodes": null`
- **THEN** the podcast's `recap_lookback_episodes` is set to null and the system falls back to the global default
