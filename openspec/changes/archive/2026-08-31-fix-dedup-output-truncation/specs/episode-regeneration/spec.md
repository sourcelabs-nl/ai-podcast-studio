## MODIFIED Requirements

### Requirement: Regenerate episode script
The system SHALL provide an endpoint to regenerate an episode's script by re-running only the composition stage of the LLM pipeline with the episode's original articles and the podcast's current configuration.

The system SHALL verify that the source episode has linked articles BEFORE creating the new GENERATING episode. Regeneration recomposes from the source episode's `episode_articles` rows, so an episode that failed before article selection can never be regenerated; creating an episode first and only then discovering this leaves a junk FAILED episode behind for every attempt. Episode 191 failed in the dedup stage, and two regenerate attempts against it produced exactly that, episodes 192 and 193.

#### Scenario: Successful regeneration
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/regenerate` request is received for an existing episode with linked articles
- **THEN** the system creates a new episode with a freshly composed script, links it to the same articles, and returns HTTP 202 with the new episode id

#### Scenario: Episode has no linked articles
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/regenerate` request is received for an episode with no entries in `episode_articles`
- **THEN** the system returns HTTP 409 with an error explaining that the episode has no articles to recompose and that a fresh generation is needed, and NO new episode is created

#### Scenario: Non-existing episode or podcast
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/regenerate` request is received for an episode or podcast that does not exist, or belongs to a different user/podcast
- **THEN** the system returns HTTP 404
