## MODIFIED Requirements

### Requirement: Costs tab on episode detail page
The episode detail page SHALL include a "Costs" tab alongside Script, Articles, and Publications. The tab SHALL render the breakdown returned by the episode detail API as a table with columns Stage / Model / Calls / Input tokens / Output tokens / Cost. The rows SHALL be: Scoring, Dedup, Dedup gate, Compose, Recap, TTS, Research, plus a Total footer row. Each cost cell SHALL be formatted in dollars with 4 decimal places (e.g. `$0.0042`) so sub-cent stage totals remain visible; cells with cost 0 SHALL render as `—`.

The Scoring row SHALL state how much of itself the dropped candidates account for, from the `droppedCalls` and `droppedCostCents` the API reports with that row. It SHALL read as a breakdown of the Scoring row and not as a row of its own, so it is never read as an amount to be added to the total.

The Dedup gate row SHALL sit directly beneath Dedup, because the gate exists to relieve the clustering call and the two are read against each other.

#### Scenario: Costs tab shows per-stage rows
- **WHEN** the user opens the Costs tab on an episode generated with every stage
- **THEN** the table shows seven rows (score, dedup, dedup gate, compose, recap, tts, research) with model name, calls, tokens, and cost; plus a Total row with the sum

#### Scenario: Dropped candidates shown within the scoring row
- **WHEN** an episode scored 188 candidates of which 67 were dropped
- **THEN** the Scoring row reports 188 calls and states that 67 of them, worth the reported dropped cost, did not reach the script

#### Scenario: Episode with no dropped candidates recorded
- **WHEN** an episode reports zero `droppedCalls`
- **THEN** the Scoring row shows no dropped breakdown at all, rather than a breakdown reading zero

#### Scenario: Legacy notice for pre-V57 episodes
- **WHEN** the user opens the Costs tab on an episode where all LLM stage cost cells are 0 but TTS or research cost is non-zero
- **THEN** an italic notice "Detailed per-stage breakdown is not available for episodes generated before this feature shipped..." is shown above the table

#### Scenario: Costs tab handles missing data gracefully
- **WHEN** the API response omits the `costs` field (legacy response shape or error)
- **THEN** the tab renders an italic "Cost breakdown is not available for this episode." message instead of an empty table

#### Scenario: Total reflects sum of all rows
- **WHEN** the table renders
- **THEN** the Total footer equals the sum of all seven rows' cost cents (formatted in dollars), and the dropped breakdown within the Scoring row is not added to it
