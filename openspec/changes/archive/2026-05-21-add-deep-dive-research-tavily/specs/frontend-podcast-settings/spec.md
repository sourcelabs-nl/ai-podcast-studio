## ADDED Requirements

### Requirement: Settings UI exposes deepDiveEnabled toggle

The podcast settings page SHALL include a toggle control for `deepDiveEnabled`. The control SHALL default to off and SHALL communicate that enabling it requires a configured Tavily key. Saving SHALL round-trip the value through `PUT /users/{userId}/podcasts/{podcastId}`.

#### Scenario: User toggles deep-dive

- **WHEN** the user enables the deep-dive toggle and clicks Save
- **THEN** the PUT request body includes `deepDiveEnabled=true` and the value persists on reload

### Requirement: API keys page exposes Tavily entry

The API keys section SHALL include an entry for the research provider (Tavily). Users can add, update, and remove a Tavily key through this entry. UI text SHALL clarify that the key is only used when `deepDiveEnabled=true` on a podcast.

#### Scenario: User saves Tavily key

- **WHEN** the user enters a Tavily API key in the API keys page and saves
- **THEN** the PUT request is sent to `/users/{userId}/api-keys/research` with `provider="tavily"`
