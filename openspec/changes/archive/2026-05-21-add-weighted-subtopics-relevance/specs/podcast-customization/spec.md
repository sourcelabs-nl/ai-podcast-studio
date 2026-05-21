## ADDED Requirements

### Requirement: Subtopics per podcast
Each podcast SHALL have an optional `subtopics` field (TEXT, nullable, stored as JSON). The JSON value SHALL be a map of subtopic name (non-empty string) to weight (integer, 1-10). The field SHALL be accepted in podcast create (`POST`) and update (`PUT`) endpoints and returned by GET. The field SHALL be serialized to/from JSON using the same Spring Data JDBC converter pattern used by `llmModels`, `ttsVoices`, etc. An empty map (`{}`) SHALL clear the field to null per the existing clearing-nullable-fields convention. Validation: any weight outside the 1-10 range or any empty key SHALL result in HTTP 400.

#### Scenario: Create podcast with subtopics
- **WHEN** a `POST /users/{userId}/podcasts` request includes `subtopics: {"LLM releases": 10, "Dev tools": 5}`
- **THEN** the podcast is created with `subtopics` persisted as that JSON map

#### Scenario: Update podcast subtopics
- **WHEN** a `PUT /users/{userId}/podcasts/{podcastId}` request includes `subtopics: {"X": 8}`
- **THEN** the podcast's `subtopics` is updated to the new value

#### Scenario: Clear subtopics with empty map
- **WHEN** a `PUT` request includes `subtopics: {}`
- **THEN** the podcast's `subtopics` is cleared to null

#### Scenario: Get podcast includes subtopics
- **WHEN** a `GET /users/{userId}/podcasts/{podcastId}` request is received
- **THEN** the response includes `subtopics` (the JSON map, or null if not set)

#### Scenario: Invalid weight rejected
- **WHEN** a `POST` or `PUT` request includes `subtopics: {"X": 0}` or `subtopics: {"Y": 11}`
- **THEN** the request is rejected with HTTP 400

### Requirement: Rapid-fire weight threshold per podcast
Each podcast SHALL have a `rapidFireWeightThreshold` field (INTEGER, NOT NULL, default 3). Valid range: 0-10. The field SHALL be accepted in podcast create and update endpoints and included in GET responses. When not provided on create, the value SHALL default to 3.

#### Scenario: Default on create
- **WHEN** a `POST /users/{userId}/podcasts` request does not include `rapidFireWeightThreshold`
- **THEN** the podcast is created with `rapidFireWeightThreshold` = 3

#### Scenario: Override on create
- **WHEN** a `POST` request includes `rapidFireWeightThreshold: 5`
- **THEN** the podcast is created with `rapidFireWeightThreshold` = 5

#### Scenario: Update threshold
- **WHEN** a `PUT` request includes `rapidFireWeightThreshold: 4`
- **THEN** the field is updated to 4

#### Scenario: Threshold out of range rejected
- **WHEN** a `POST` or `PUT` request includes `rapidFireWeightThreshold: -1` or `rapidFireWeightThreshold: 11`
- **THEN** the request is rejected with HTTP 400

#### Scenario: Get podcast includes threshold
- **WHEN** a `GET` request is received
- **THEN** the response includes `rapidFireWeightThreshold` with its current value
