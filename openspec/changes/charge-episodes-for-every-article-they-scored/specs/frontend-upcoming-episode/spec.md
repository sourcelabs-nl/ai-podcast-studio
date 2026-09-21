## ADDED Requirements

### Requirement: Costs tab on the upcoming content page
The upcoming content page SHALL include a "Costs" tab alongside Articles and Script, rendering what has been spent scoring the articles currently standing, in the same table shape the episode detail page uses: Stage / Model / Calls / Input tokens / Output tokens / Cost, with a Total footer row. Cost cells SHALL be formatted in dollars with 4 decimal places and a zero cost SHALL render as `—`.

Only the scoring stage has run at this point, so it is the only row with figures. The stages that run during generation SHALL NOT be listed as empty rows, because a stage shown at zero reads as a stage that cost nothing rather than one that has not happened.

#### Scenario: Scoring spend shown before generation
- **WHEN** the user opens the Costs tab with 60 articles standing
- **THEN** the table shows one Scoring row with the model, 60 calls, the summed tokens and the cost, and a Total row equal to it

#### Scenario: Nothing standing yet
- **WHEN** the user opens the Costs tab and no articles are standing
- **THEN** the tab states that nothing has been scored for the next episode yet, instead of a table of zeroes
