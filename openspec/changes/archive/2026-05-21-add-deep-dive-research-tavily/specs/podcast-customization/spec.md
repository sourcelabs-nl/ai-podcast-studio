## ADDED Requirements

### Requirement: deepDiveEnabled podcast field

A podcast SHALL accept a boolean `deepDiveEnabled` field (default `false`) controlling whether the compose stage registers the `webSearch` research tool. The field SHALL be persisted on the `podcasts` table and exposed through the create/update/get endpoints.

#### Scenario: Field round-trips through the API

- **WHEN** a client sends `PUT /users/{userId}/podcasts/{podcastId}` with `deepDiveEnabled=true`
- **THEN** the value is persisted and the next `GET` for the same podcast returns `deepDiveEnabled=true`

#### Scenario: Default is false

- **WHEN** a podcast is created without an explicit `deepDiveEnabled` value
- **THEN** the persisted row has `deep_dive_enabled=0`
