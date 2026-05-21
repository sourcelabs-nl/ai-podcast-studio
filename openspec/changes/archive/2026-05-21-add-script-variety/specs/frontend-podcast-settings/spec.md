## ADDED Requirements

### Requirement: Settings UI exposes compose temperature

The podcast settings page SHALL include a numeric input for the compose temperature constrained to `[0.0, 2.0]` with step `0.05`. The input is bound to the `temperature` key inside the `composeSettings` map. The input SHALL show the system default (`0.95`) as a placeholder when the value is unset. Saving SHALL round-trip the value through `PUT /users/{userId}/podcasts/{podcastId}` as `composeSettings = {"temperature": "<value>"}`.

#### Scenario: User edits temperature

- **WHEN** the user changes the compose temperature field to `0.7` and clicks Save
- **THEN** the PUT request body includes `composeSettings = {"temperature": "0.7"}` and the value persists on reload

#### Scenario: Unset temperature shows placeholder

- **WHEN** the settings page loads for a podcast with no `composeSettings.temperature` override
- **THEN** the input renders empty with placeholder text indicating the default `0.95`

#### Scenario: Clearing the input clears only the temperature key

- **WHEN** the user empties the temperature input and clicks Save
- **THEN** the PUT request omits the `temperature` key from `composeSettings` (preserving any other keys present) — or sends `composeSettings = {}` when temperature was the only key, which clears the map per the customization spec
