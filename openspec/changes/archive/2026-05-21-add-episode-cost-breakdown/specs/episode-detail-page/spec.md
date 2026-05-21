## ADDED Requirements

### Requirement: Costs tab on episode detail page
The episode detail page SHALL include a "Costs" tab alongside Script, Articles, and Publications. The tab SHALL render the breakdown returned by the episode detail API as a table with columns Stage / Model / Calls / Input tokens / Output tokens / Cost. The rows SHALL be: Scoring, Dedup, Compose, Recap, TTS, Research, plus a Total footer row. Each cost cell SHALL be formatted in dollars with 4 decimal places (e.g. `$0.0042`) so sub-cent stage totals remain visible; cells with cost 0 SHALL render as `—`.

#### Scenario: Costs tab shows per-stage rows
- **WHEN** the user opens the Costs tab on an episode generated with all six stages
- **THEN** the table shows six rows (score, dedup, compose, recap, tts, research) with model name, calls, tokens, and cost; plus a Total row with the sum

#### Scenario: Legacy notice for pre-V57 episodes
- **WHEN** the user opens the Costs tab on an episode where all four LLM stage cost cells are 0 but TTS or research cost is non-zero
- **THEN** an italic notice "Detailed per-stage breakdown is not available for episodes generated before this feature shipped..." is shown above the table

#### Scenario: Costs tab handles missing data gracefully
- **WHEN** the API response omits the `costs` field (legacy response shape or error)
- **THEN** the tab renders an italic "Cost breakdown is not available for this episode." message instead of an empty table

#### Scenario: Total reflects sum of all rows
- **WHEN** the table renders
- **THEN** the Total footer equals the sum of all six rows' cost cents (formatted in dollars)
