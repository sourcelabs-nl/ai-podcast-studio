## MODIFIED Requirements

### Requirement: Podcast entity scoped to user
The system SHALL store podcasts in a `podcasts` database table with columns: `id` (TEXT, primary key, UUID), `user_id` (TEXT, FK to users, NOT NULL), `name` (TEXT, NOT NULL), `topic` (TEXT, NOT NULL), `require_review` (INTEGER, NOT NULL, default 0), and `timezone` (TEXT, NOT NULL, default 'UTC'). The `id` SHALL be generated as a UUID v4 upon creation. A user MAY have multiple podcasts, each with a different topic. The `require_review` field controls whether episode scripts require manual review before TTS generation. The `timezone` field stores an IANA timezone identifier (e.g., `Europe/Amsterdam`, `America/New_York`, `UTC`) used for evaluating the podcast's cron schedule. The `requireReview` field in the JSON request body SHALL be deserialized correctly for both `true` and `false` values when explicitly provided; Jackson 3 `@JsonProperty` annotations SHALL be used on nullable primitive DTO fields to ensure correct deserialization.

#### Scenario: Create a podcast
- **WHEN** a `POST /users/{userId}/podcasts` request is received with a JSON body containing `name` and `topic`
- **THEN** the system creates a podcast record linked to the user with a generated UUID, `timezone` defaulting to `UTC`, and returns the created podcast with HTTP 201

#### Scenario: Create a podcast with timezone
- **WHEN** a `POST /users/{userId}/podcasts` request is received with a JSON body containing `name`, `topic`, and `timezone: "Europe/Amsterdam"`
- **THEN** the system creates a podcast with `timezone` set to `Europe/Amsterdam` and the response body SHALL contain `"timezone": "Europe/Amsterdam"`

#### Scenario: Create a podcast with invalid timezone
- **WHEN** a `POST /users/{userId}/podcasts` request is received with `timezone: "Invalid/Zone"`
- **THEN** the system returns HTTP 400 with a validation error message

#### Scenario: Create a podcast with review enabled
- **WHEN** a `POST /users/{userId}/podcasts` request is received with a JSON body containing `name`, `topic`, and `requireReview: true`
- **THEN** the system creates a podcast with `require_review` set to true and the response body SHALL contain `"requireReview": true`

#### Scenario: Update podcast to enable review
- **WHEN** a `PUT /users/{userId}/podcasts/{podcastId}` request is received with `requireReview: true`
- **THEN** the system updates the podcast's `require_review` to true and the response body SHALL contain `"requireReview": true`

#### Scenario: Create podcast for non-existing user
- **WHEN** a `POST /users/{userId}/podcasts` request is received for a user that does not exist
- **THEN** the system returns HTTP 404

#### Scenario: Create podcast with missing fields
- **WHEN** a `POST /users/{userId}/podcasts` request is received without `name` or `topic`
- **THEN** the system returns HTTP 400 with a validation error message

### Requirement: Update podcast
The system SHALL allow updating a podcast's `name`, `topic`, `requireReview`, and `timezone` fields. When `timezone` is provided, the system SHALL validate it using `java.time.ZoneId.of()` and return HTTP 400 if the value is not a valid IANA timezone identifier.

#### Scenario: Update podcast name and topic
- **WHEN** a `PUT /users/{userId}/podcasts/{podcastId}` request is received with a JSON body containing `name` and/or `topic`
- **THEN** the system updates the podcast record and returns HTTP 200 with the updated podcast

#### Scenario: Update podcast timezone
- **WHEN** a `PUT /users/{userId}/podcasts/{podcastId}` request is received with `timezone: "Europe/Amsterdam"`
- **THEN** the system updates the podcast's `timezone` to `Europe/Amsterdam` and returns HTTP 200

#### Scenario: Update podcast with invalid timezone
- **WHEN** a `PUT /users/{userId}/podcasts/{podcastId}` request is received with `timezone: "Not/A/Zone"`
- **THEN** the system returns HTTP 400 with a validation error message

#### Scenario: Enable review on existing podcast
- **WHEN** a `PUT /users/{userId}/podcasts/{podcastId}` request is received with `requireReview: true`
- **THEN** the system updates the podcast's `require_review` to true; subsequent briefing generations will create pending episodes instead of auto-generating audio

#### Scenario: Update non-existing podcast
- **WHEN** a `PUT /users/{userId}/podcasts/{podcastId}` request is received for a podcast that does not exist or belongs to a different user
- **THEN** the system returns HTTP 404