## ADDED Requirements

### Requirement: Inworld TTS-2 delivery mode selector
The TTS tab SHALL display a dedicated "Delivery Mode" `Select` control for Inworld TTS-2. The selector SHALL be rendered only when `ttsProvider` is `inworld` AND the selected TTS model (`ttsSettings.model`) is `inworld-tts-2`. The selector SHALL offer four options: an unset placeholder labeled `— (provider default)`, `STABLE`, `BALANCED`, and `EXPRESSIVE`. The selected value SHALL be persisted in `form.ttsSettings.deliveryMode`. Selecting the unset placeholder SHALL remove the `deliveryMode` key from `ttsSettings` rather than persisting an empty string.

The generic `KeyValueEditor` for `ttsSettings` SHALL remain available below the dedicated controls so advanced users can override or add other Inworld parameters.

#### Scenario: Delivery Mode shown for Inworld TTS-2
- **WHEN** the TTS tab is active, `ttsProvider` is `inworld`, and `ttsSettings.model` is `inworld-tts-2`
- **THEN** a Delivery Mode dropdown is rendered with options `— (provider default)`, `STABLE`, `BALANCED`, and `EXPRESSIVE`

#### Scenario: Delivery Mode hidden for other Inworld models
- **WHEN** the TTS tab is active, `ttsProvider` is `inworld`, and `ttsSettings.model` is `inworld-tts-1.5-max` or `inworld-tts-1.5-mini`
- **THEN** the Delivery Mode dropdown is NOT rendered

#### Scenario: Delivery Mode hidden for non-Inworld providers
- **WHEN** the TTS tab is active and `ttsProvider` is `openai` or `elevenlabs`
- **THEN** the Delivery Mode dropdown is NOT rendered regardless of model selection

#### Scenario: Selecting an enum value persists to ttsSettings
- **WHEN** the user selects `EXPRESSIVE` from the Delivery Mode dropdown
- **THEN** `form.ttsSettings.deliveryMode` is set to `"EXPRESSIVE"`

#### Scenario: Selecting unset removes the key
- **WHEN** the user selects `— (provider default)` from the Delivery Mode dropdown
- **THEN** the `deliveryMode` key is removed from `form.ttsSettings` (not persisted as an empty string)

## MODIFIED Requirements

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
