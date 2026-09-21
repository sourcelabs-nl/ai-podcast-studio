## ADDED Requirements

### Requirement: Upcoming articles response reports what scoring them cost
The `GET /users/{userId}/podcasts/{podcastId}/upcoming-articles` endpoint SHALL report what has been spent scoring the articles currently standing as candidates for the next episode: the number of scoring requests, their input and output tokens, and their cost.

The figures SHALL be computed the same way an episode's score stage is: provider-reported per-article costs summed where present, the remainder estimated from the summed tokens and the configured rate of the scoring model, so the number shown before generation and the number shown after it are comparable.

The spend is reported for the articles the endpoint is already reporting. No article is scored in order to answer this request.

#### Scenario: Upcoming spend reported
- **WHEN** 60 articles are standing for the next episode and each has been scored
- **THEN** the response reports 60 scoring requests with their summed tokens and their cost

#### Scenario: No articles standing
- **WHEN** no articles are standing for the next episode
- **THEN** the response reports zero requests, zero tokens and zero cost

#### Scenario: Articles scored before per-article cost was captured
- **WHEN** some standing articles carry token counts but no reported cost
- **THEN** their part of the total is estimated from their tokens and the configured rate, as it is for an episode
