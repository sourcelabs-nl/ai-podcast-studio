## Purpose

Defines the requirements for the podcast settings page in the Next.js frontend dashboard, enabling users to view and edit all podcast configuration through a tabbed form interface.
## Requirements
### Requirement: Settings page route
The system SHALL provide a settings page at `/podcasts/[podcastId]/settings` that fetches the podcast from `GET /users/{userId}/podcasts/{podcastId}` and displays all configuration fields in an editable form.

#### Scenario: Load settings page
- **WHEN** user navigates to `/podcasts/{podcastId}/settings`
- **THEN** the page fetches the podcast data and displays a "Back to podcast" link, the page title "Settings: {podcastName}", and a tabbed settings form

#### Scenario: Podcast not found
- **WHEN** the podcast ID does not exist or does not belong to the selected user
- **THEN** the page displays "Podcast not found."

### Requirement: Settings sub-tabs
The settings form SHALL be organized into five sub-tabs: General, LLM, TTS, Content, and Publishing. The General tab SHALL be the default active tab.

#### Scenario: General tab fields
- **WHEN** the General tab is active
- **THEN** the form displays, in order: podcast image (upload/delete, shown first), name (text), topic (text), language (text), style (select), cron (text), requireReview (checkbox)

#### Scenario: LLM tab fields
- **WHEN** the LLM tab is active
- **THEN** the form displays editable fields for: LLM Models (per-stage provider/model dropdowns with defaults shown), relevanceThreshold (number), maxLlmCostCents (number), customInstructions (textarea with 300px min height)

#### Scenario: TTS tab fields
- **WHEN** the TTS tab is active
- **THEN** the form displays editable fields for: ttsProvider (select), ttsModel (select, populated from `availableModels` for the selected provider), Delivery Mode (select, conditionally shown only when provider=`inworld` and model=`inworld-tts-2`), ttsVoices (key-value editor), ttsSettings (key-value editor for advanced overrides), speakerNames (key-value editor)

#### Scenario: Content tab fields
- **WHEN** the Content tab is active
- **THEN** the form displays editable fields for: targetWords (number), fullBodyThreshold (number), maxArticleAgeDays (number), recapLookbackEpisodes (number), sponsor (key-value editor), pronunciations (key-value editor)

> **Known gap:** `recapLookbackEpisodes` is present in the backend API but is not yet included in the settings form. It should be added to the Content tab as a number field.

### Requirement: LLM model selector
The LLM Models field SHALL display a per-stage model selector for the `filter` and `compose` pipeline stages. For each stage, the selector SHALL show: a provider dropdown (populated from `availableModels` providers that have at least one LLM-type model), and a model dropdown (populated from the selected provider's LLM-type models from `availableModels`). The system default for each stage SHALL be displayed below the selector (e.g., "Default: openrouter / anthropic/claude-sonnet-4.6"). When no override is set for a stage, the dropdowns SHALL show the default values as placeholder text.

#### Scenario: Display model selector with available models
- **WHEN** the LLM tab is active and `/config/defaults` returns `availableModels` with `openrouter` containing LLM models `openai/gpt-5.4-nano` and `anthropic/claude-sonnet-4.6`
- **THEN** the provider dropdown for each stage lists `openrouter`, and selecting it shows the two LLM models in the model dropdown

#### Scenario: Show defaults when no override set
- **WHEN** the podcast has no `llmModels` override for the `compose` stage and the default is `{provider: openrouter, model: anthropic/claude-sonnet-4.6}`
- **THEN** the compose stage selector shows "Default: openrouter / anthropic/claude-sonnet-4.6" and the dropdowns use the default values as placeholders

#### Scenario: Override a stage model
- **WHEN** the user selects provider `openrouter` and model `anthropic/claude-opus-4.7` for the compose stage
- **THEN** the form stores `llmModels.compose` as `{"provider": "openrouter", "model": "anthropic/claude-opus-4.7"}`

#### Scenario: Clear an override
- **WHEN** the user clears the override for a stage (resets to default)
- **THEN** that stage key is removed from the `llmModels` map

#### Scenario: Only LLM models shown in LLM dropdowns
- **WHEN** `availableModels` contains both LLM and TTS models under various providers
- **THEN** only models with `type: "llm"` appear in the LLM model dropdowns

### Requirement: Save settings
The system SHALL display a single Save button at the bottom of the settings page. Clicking Save SHALL send a `PUT /users/{userId}/podcasts/{podcastId}` request with all current form values.

#### Scenario: Successful save
- **WHEN** user clicks Save and the API returns 200
- **THEN** the form updates with the response data and a success indication is shown

#### Scenario: Save with API error
- **WHEN** user clicks Save and the API returns an error
- **THEN** the error message from the API response is displayed to the user

#### Scenario: Key-value serialization
- **WHEN** the form is saved and key-value editors contain rows
- **THEN** each key-value editor's rows are serialized into a JSON object (e.g., rows `[{key: "a", value: "b"}]` become `{"a": "b"}`). Empty keys are excluded. If no rows remain, the field is sent as an empty object `{}`.

#### Scenario: Clearing nullable fields
- **WHEN** the form is saved and a nullable field has been cleared by the user
- **THEN** string fields (e.g., customInstructions) are sent as an empty string `""`, map fields (e.g., llmModels) are sent as an empty object `{}`, and number fields (e.g., targetWords) are sent as `null`. The value MUST NOT be omitted from the JSON payload, as omitted fields are treated as "keep existing" by the backend.

### Requirement: System default placeholders on nullable fields
Nullable number fields in the settings form SHALL display placeholder text showing the system default value when the field is empty. The placeholder values SHALL be fetched from the `GET /config/defaults` API endpoint. The placeholder format SHALL be `{value} (system default)`.

#### Scenario: Empty max LLM cost shows default
- **WHEN** the LLM tab is active and maxLlmCostCents is null/empty
- **THEN** the input displays placeholder text with the maxLlmCostCents value from the defaults endpoint followed by "(system default)"

#### Scenario: Empty target words shows default
- **WHEN** the Content tab is active and targetWords is null/empty
- **THEN** the input displays placeholder text with the targetWords value from the defaults endpoint followed by "(system default)"

#### Scenario: Empty full body threshold shows default
- **WHEN** the Content tab is active and fullBodyThreshold is null/empty
- **THEN** the input displays placeholder text with the fullBodyThreshold value from the defaults endpoint followed by "(system default)"

#### Scenario: Empty max article age shows default
- **WHEN** the Content tab is active and maxArticleAgeDays is null/empty
- **THEN** the input displays placeholder text with the maxArticleAgeDays value from the defaults endpoint followed by "(system default)"

#### Scenario: Explicit value hides placeholder
- **WHEN** a nullable field has an explicit user-set value
- **THEN** the placeholder is not visible and the user's value is displayed

### Requirement: LLM models default hint
The LLM Models selector SHALL display a "Using system default" hint per-stage when no override is set, with the actual default model information fetched from the `GET /config/defaults` endpoint.

#### Scenario: Default models displayed
- **WHEN** the LLM tab is active and defaults have been loaded
- **THEN** a helper text is displayed below each stage's selector showing "Using system default" when no override is set for that stage

#### Scenario: Defaults not loaded
- **WHEN** the defaults endpoint fails or has not loaded yet
- **THEN** no helper text or placeholder is displayed

### Requirement: Podcast type completeness
The `Podcast` TypeScript interface SHALL include all fields returned by the `GET /users/{userId}/podcasts/{podcastId}` API endpoint: id, userId, name, topic, language, llmModels, ttsProvider, ttsVoices, ttsSettings, style, targetWords, cron, timezone, customInstructions, relevanceThreshold, requireReview, maxLlmCostCents, maxArticleAgeDays, speakerNames, fullBodyThreshold, sponsor, pronunciations, lastGeneratedAt.

#### Scenario: Timezone field available in type
- **WHEN** the frontend fetches podcast data
- **THEN** the `timezone` field is available on the Podcast type

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

### Requirement: Inworld TTS-2 delivery mode selector
The TTS tab SHALL display a dedicated "Delivery Mode" `Select` control for Inworld TTS-2. The selector SHALL be rendered only when `ttsProvider` is `inworld` AND the selected TTS model (`ttsSettings.model`) is `inworld-tts-2`. The selector SHALL offer four options: an unset placeholder labeled `— (provider default)`, `STABLE`, `BALANCED`, and `CREATIVE`. `EXPRESSIVE` is not a value of the Inworld enum and SHALL NOT be offered. The selected value SHALL be persisted in `form.ttsSettings.deliveryMode`. Selecting the unset placeholder SHALL remove the `deliveryMode` key from `ttsSettings` rather than persisting an empty string.

The generic `KeyValueEditor` for `ttsSettings` SHALL remain available below the dedicated controls so advanced users can override or add other Inworld parameters.

#### Scenario: Delivery Mode shown for Inworld TTS-2
- **WHEN** the TTS tab is active, `ttsProvider` is `inworld`, and `ttsSettings.model` is `inworld-tts-2`
- **THEN** a Delivery Mode dropdown is rendered with options `— (provider default)`, `STABLE`, `BALANCED`, and `CREATIVE`

#### Scenario: Delivery Mode hidden for other Inworld models
- **WHEN** the TTS tab is active, `ttsProvider` is `inworld`, and `ttsSettings.model` is `inworld-tts-1.5-max` or `inworld-tts-1.5-mini`
- **THEN** the Delivery Mode dropdown is NOT rendered

#### Scenario: Delivery Mode hidden for non-Inworld providers
- **WHEN** the TTS tab is active and `ttsProvider` is `openai` or `elevenlabs`
- **THEN** the Delivery Mode dropdown is NOT rendered regardless of model selection

#### Scenario: Selecting an enum value persists to ttsSettings
- **WHEN** the user selects `CREATIVE` from the Delivery Mode dropdown
- **THEN** `form.ttsSettings.deliveryMode` is set to `"CREATIVE"`

#### Scenario: Selecting unset removes the key
- **WHEN** the user selects `— (provider default)` from the Delivery Mode dropdown
- **THEN** the `deliveryMode` key is removed from `form.ttsSettings` (not persisted as an empty string)

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

