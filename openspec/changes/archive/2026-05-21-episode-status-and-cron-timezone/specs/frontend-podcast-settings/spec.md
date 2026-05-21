## MODIFIED Requirements

### Requirement: Settings sub-tabs
The settings form SHALL be organized into five sub-tabs: General, LLM, TTS, Content, and Publishing. The General tab SHALL be the default active tab.

#### Scenario: General tab fields
- **WHEN** the General tab is active
- **THEN** the form displays, in order: podcast image (upload/delete, shown first), name (text), topic (text), language (text), style (select), cron (text), timezone (text input with datalist of common IANA timezones), requireReview (checkbox)

#### Scenario: Timezone field displays current value
- **WHEN** the General tab is active and the podcast has `timezone: "Europe/Amsterdam"`
- **THEN** the timezone field shows "Europe/Amsterdam" as its value

#### Scenario: Timezone field default
- **WHEN** the General tab is active and the podcast has `timezone: "UTC"` (default)
- **THEN** the timezone field shows "UTC" as its value

#### Scenario: Timezone field suggestions
- **WHEN** the user focuses or types in the timezone field
- **THEN** a datalist provides common timezone suggestions including UTC, Europe/Amsterdam, Europe/London, Europe/Berlin, America/New_York, America/Chicago, America/Denver, America/Los_Angeles, Asia/Tokyo, Asia/Shanghai, Australia/Sydney

### Requirement: Podcast type completeness
The `Podcast` TypeScript interface SHALL include all fields returned by the `GET /users/{userId}/podcasts/{podcastId}` API endpoint: id, userId, name, topic, language, llmModels, ttsProvider, ttsVoices, ttsSettings, style, targetWords, cron, timezone, customInstructions, relevanceThreshold, requireReview, maxLlmCostCents, maxArticleAgeDays, speakerNames, fullBodyThreshold, sponsor, pronunciations, lastGeneratedAt.

#### Scenario: Timezone field available in type
- **WHEN** the frontend fetches podcast data
- **THEN** the `timezone` field is available on the Podcast type