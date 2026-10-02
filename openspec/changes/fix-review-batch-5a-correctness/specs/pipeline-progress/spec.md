# Spec Delta

## MODIFIED Requirements

### Requirement: Pipeline status endpoint
The system SHALL provide a `GET /users/{userId}/podcasts/{podcastId}/pipeline-status` endpoint that returns the current pipeline stage for a podcast. The stage SHALL be the pipeline stage persisted on the podcast's newest episode in GENERATING status that has a recorded stage, so it survives an application restart and is absent once that episode completes, fails or is deleted.

#### Scenario: Pipeline status when generation is active
- **WHEN** a `GET /users/{userId}/podcasts/{podcastId}/pipeline-status` request is received while the LLM pipeline is in the "composing" stage
- **THEN** the system returns HTTP 200 with `{"stage": "composing"}`

#### Scenario: Pipeline status when idle
- **WHEN** a `GET /users/{userId}/podcasts/{podcastId}/pipeline-status` request is received and no pipeline is running
- **THEN** the system returns HTTP 200 with `{"stage": null}`

#### Scenario: Pipeline status after a run found nothing to compose
- **WHEN** a generation run deleted its placeholder episode because no articles were eligible
- **THEN** a `GET .../pipeline-status` request returns HTTP 200 with `{"stage": null}`
