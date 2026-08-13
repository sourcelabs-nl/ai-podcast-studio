## MODIFIED Requirements

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
