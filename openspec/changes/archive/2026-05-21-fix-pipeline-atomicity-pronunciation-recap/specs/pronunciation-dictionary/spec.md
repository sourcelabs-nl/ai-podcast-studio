## MODIFIED Requirements

### Requirement: Per-podcast pronunciation dictionary
Each podcast SHALL have an optional `pronunciations` field (TEXT, nullable, stored as JSON map). The map keys SHALL be terms (proper nouns, brand names, or foreign names) and values SHALL be IPA phoneme notation strings (e.g., `{"Anthropic": "/ænˈθɹɒpɪk/", "Jarno": "/jɑrnoː/", "Stephan": "/ˈsteːfɑn/", "Sourcelabs": "/sɔːrsˌlæbz/"}`). When the pronunciation dictionary is non-empty and the TTS provider supports IPA phonemes, the dictionary SHALL be injected into the LLM script guidelines as a "Pronunciation Guide" section. The pronunciation guide instruction SHALL explicitly state that IPA notation must ONLY be used for the exact terms listed in the pronunciation dictionary. The instruction SHALL use "CRITICAL" emphasis to prevent LLMs from inventing IPA for unlisted words (e.g., adding `/eɪˈdʒɛntɪk/` for "Agentic" when it is not in the dictionary). The LLM SHALL be instructed to REPLACE the word with its IPA phoneme notation on EVERY occurrence (not write both the word and the phoneme side by side). When the dictionary is null or empty, no pronunciation section SHALL be added to the guidelines.

#### Scenario: Podcast with pronunciation dictionary
- **WHEN** a podcast has `pronunciations` set to `{"Anthropic": "/ænˈθɹɒpɪk/", "LLaMA": "/ˈlɑːmə/"}`
- **AND** the TTS provider is Inworld
- **THEN** the script guidelines include a "Pronunciation Guide" section listing both terms with their IPA phonemes

#### Scenario: Podcast with empty pronunciation dictionary
- **WHEN** a podcast has `pronunciations` set to `{}`
- **THEN** no pronunciation section is added to the script guidelines

#### Scenario: Podcast with null pronunciation dictionary
- **WHEN** a podcast has `pronunciations` set to null
- **THEN** no pronunciation section is added to the script guidelines

#### Scenario: Every occurrence replacement instruction
- **WHEN** pronunciations are injected into guidelines
- **THEN** the guidelines instruct the LLM to REPLACE the word with its IPA phoneme on EVERY occurrence (not just the first), and to ONLY use IPA for listed terms
