## ADDED Requirements

### Requirement: Per-episode prompt variety rotation

The compose stage SHALL deterministically rotate a set of prompt-shape dimensions per episode so that consecutive episodes from the same podcast do not produce byte-identical scaffolding. Selection MUST be a pure function of `(podcastId, episodeDate)` so that regenerating the same episode yields the same selection.

The rotated dimensions MUST include all six of:

- **Opening style** (e.g. cold-open question, shocking-stat, scene-set, contrarian take, first-person hook).
- **Transition vocabulary set** (e.g. signposts A, signposts B, signposts C).
- **Sign-off shape** (e.g. recap-first, forward-look, call-to-action, quote-of-the-day).
- **Teaser shape** (interview-only; e.g. lead-with-question, curiosity-list, cold-tease, rhetorical-hook).
- **Topic-entry pattern** (interview/dialogue; e.g. straight-question, theme-bridge, contrast-pivot, micro-recap-then-pivot).
- **Penultimate-exchange shape** (interview/dialogue; e.g. mutual-thanks, forward-look-handoff, single-sentence-callback, cold-handoff-to-sign-off).

Briefing/casual/deep-dive/executive-summary composers SHALL consume the first three dimensions; interview and dialogue composers SHALL consume all six.

#### Scenario: Same episode regenerated gets the same selection

- **WHEN** the compose stage runs twice for the same `(podcastId, episodeDate)`
- **THEN** the variety picker returns identical selections on all six dimensions both times

#### Scenario: Consecutive days produce different selections

- **WHEN** the compose stage runs for the same podcast on five consecutive dates
- **THEN** at least three of the five episodes have a distinct opening-style selection AND at least three have a distinct sign-off-shape selection

#### Scenario: Interview style consumes all six axes

- **WHEN** the interview composer builds a prompt
- **THEN** the prompt content reflects the picker's teaser-shape, topic-entry-pattern, and penultimate-exchange-shape selections in addition to the three shared axes

### Requirement: Compose-stage temperature is configurable

The compose stage SHALL pass a `temperature` value to the LLM chat options. The temperature SHALL default to the system value `0.95` (configured via `app.briefing.default-temperature`) and SHALL be overridable per podcast by setting the `temperature` key inside the podcast's `composeSettings` map. Temperature MUST be clamped to `[0.0, 2.0]` server-side.

#### Scenario: Default temperature is applied when composeSettings is null

- **WHEN** a podcast has `composeSettings = null`
- **THEN** the compose stage calls the LLM with `temperature=0.95`

#### Scenario: Default temperature is applied when key is missing

- **WHEN** a podcast has `composeSettings = {}` or a map that does not include `"temperature"`
- **THEN** the compose stage calls the LLM with `temperature=0.95`

#### Scenario: Per-podcast temperature override is applied

- **WHEN** a podcast has `composeSettings = {"temperature": "0.6"}`
- **THEN** the compose stage calls the LLM with `temperature=0.6`

#### Scenario: Out-of-range temperature is rejected

- **WHEN** the API receives a podcast update with `composeSettings = {"temperature": "3.5"}`
- **THEN** the value is rejected with HTTP 422 with a message identifying the allowed range

#### Scenario: Unrecognised composeSettings keys persist but are ignored at compose time

- **WHEN** a client sends `composeSettings = {"temperature": "0.7", "unknown": "value"}`
- **THEN** the persisted map round-trips both keys but the compose stage uses only `temperature=0.7`

### Requirement: Composer prompts contain no verbatim example phrases

The compose-stage prompts for monologue, dialogue, and interview composers SHALL NOT include verbatim example sentences that have previously appeared in generated scripts (e.g. literal `"But here's where it gets really interesting..."`, literal `"Coming up: AI agents going rogue..."`, literal `"Stephan, thanks as always"`, literal interruption samples like `"Wait, wait — did you say 100x?!"`). Structural beats MAY be described in abstract terms; concrete sample phrases that the LLM is prone to copy verbatim MUST be removed.

#### Scenario: Prompt contains no banned phrases

- **WHEN** the compose prompt is built for any style (`news-briefing`, `casual`, `deep-dive`, `executive-summary`, `dialogue`, `interview`)
- **THEN** the prompt string does not contain any of the documented banned example phrases verbatim
