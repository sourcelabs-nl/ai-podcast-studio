## ADDED Requirements

### Requirement: composeSettings podcast field

A podcast SHALL accept an optional `composeSettings` field — a `Map<String, String>` mirroring the existing `ttsSettings` shape. For v1 the only recognised key is `"temperature"` (parsed as a decimal in `[0.0, 2.0]`); other keys MAY be persisted but MUST be ignored by the compose stage. The field SHALL be persisted on the `podcasts` table as `compose_settings TEXT` (JSON), returned by the podcast GET endpoint, and accepted by the create/update endpoints.

When `composeSettings` is null or the `temperature` key is absent, the compose stage applies the system default of `0.95` (configured via `app.briefing.default-temperature`).

#### Scenario: Field round-trips through the API

- **WHEN** a client sends `PUT /users/{userId}/podcasts/{podcastId}` with `composeSettings = {"temperature": "0.7"}`
- **THEN** the value is persisted and the next `GET` for the same podcast returns `composeSettings.temperature == "0.7"`

#### Scenario: Out-of-range temperature rejected

- **WHEN** a client sends `composeSettings = {"temperature": "2.5"}`
- **THEN** the API returns HTTP 422 with a message identifying the allowed range

#### Scenario: Unparseable temperature rejected

- **WHEN** a client sends `composeSettings = {"temperature": "warm"}`
- **THEN** the API returns HTTP 422

#### Scenario: Null map falls back to default

- **WHEN** a podcast is created without an explicit `composeSettings`
- **THEN** the persisted column is `NULL` and the compose stage uses `temperature=0.95`

#### Scenario: Update with orKeep semantics

- **WHEN** a client sends `PUT` without `composeSettings` (key absent)
- **THEN** the existing persisted value is preserved (parity with how `ttsSettings` is handled)

#### Scenario: Update can clear the map

- **WHEN** a client sends `PUT` with `composeSettings = {}` (empty map)
- **THEN** the persisted column becomes `NULL` (parity with how `ttsSettings` is cleared)
