## ADDED Requirements

### Requirement: Deterministic script metrics
The system SHALL compute, without any model call, metrics that are a pure function of an episode script: the number of speaker turns, the turn and word count per role, the median, maximum and over-threshold count of expert turn lengths, and which role owns each `[laugh]` tag.

These metrics SHALL be stable across judge versions, so an ablation can run them on every generation at no cost.

`[laugh]` ownership SHALL be reported as a proxy for humor distribution and never as a humor count, since a joke carrying no laugh tag is invisible to it.

#### Scenario: Metrics computed without a model
- **WHEN** `ScriptMetrics` is given a script
- **THEN** it returns the counts and distributions with no LLM call

#### Scenario: A script with one role only
- **WHEN** a script contains no expert turns
- **THEN** the expert statistics are empty rather than an error

### Requirement: The judge returns anchors rather than measurements
`ScriptJudge` SHALL make one model call per script and return positions in the script: the turn index of each forward-looking promise and of the turn that pays it off, the turn index and role of each humor beat together with whether it reacts to the immediately preceding turn, and the distinct topics named in the introduction teaser.

The judge SHALL NOT be asked for distances, counts, ratios or an overall rating. Deferral distance, speaker balance and every derived figure SHALL be computed from the returned indices outside the model.

An anchor can be checked by opening the script at that turn, which is what makes the judge auditable; a count or a rating cannot be checked and would drift silently between judge model versions.

#### Scenario: Distances are derived, not asked for
- **WHEN** the judge returns a promise at one turn and a payoff at another
- **THEN** the deferral distance is computed from those indices rather than taken from the model

#### Scenario: No overall rating is requested
- **WHEN** the judge prompt is built
- **THEN** it asks for positions and does not ask the model to rate the script

### Requirement: Scores are versioned and carry their own cost
Each stored score SHALL record the scorer version and the judge model that produced it, and SHALL carry its own token usage and cost.

A comparison across scores SHALL refuse to mix rows produced by different scorer versions rather than averaging them, since a change to the judge prompt changes what the numbers mean.

Scoring cost SHALL NOT be written to the episode's own cost columns, which describe what it cost to produce that episode; scoring happens afterwards and may happen many times.

#### Scenario: A re-scored episode does not change its episode cost
- **WHEN** an episode is scored
- **THEN** the episode's own cost columns are unchanged and the cost is recorded on the score

#### Scenario: Mixing scorer versions is refused
- **WHEN** scores produced by two scorer versions are compared
- **THEN** the comparison reports them separately rather than combining them

### Requirement: Scoring is reached through the API
Triggering a scoring run and reading scores back SHALL be available as REST endpoints, so scores are never obtained by querying the database directly. The controller SHALL validate input and delegate to the scoring service without holding logic of its own.

Scoring SHALL skip an episode that already has a score at the current scorer version, so re-running over the archive costs nothing for work already done.

#### Scenario: Already-scored episodes are skipped
- **WHEN** a scoring run covers an episode already scored at the current version
- **THEN** no model call is made for that episode

#### Scenario: Scores are readable over HTTP
- **WHEN** a client asks for an episode's score
- **THEN** the API returns it without the client touching the database
