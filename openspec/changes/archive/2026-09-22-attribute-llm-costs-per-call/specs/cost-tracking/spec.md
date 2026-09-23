## MODIFIED Requirements

### Requirement: Per-stage episode LLM cost breakdown
The system SHALL report per-stage LLM token and cost totals for each episode, split across four
stages: scoring (Stage 1), dedup (Stage 3), compose (Stage 4), and recap. For an episode whose
requests are attributable, each stage's totals SHALL be the sum of that episode's recorded requests
for that stage, and SHALL NOT be maintained as a second, separately written figure. A cost that was
paid is counted because the request was recorded, not because the stage that issued it completed.

The columns `score_input_tokens`, `score_output_tokens`, `score_cost_cents`, `dedup_input_tokens`,
`dedup_output_tokens`, `dedup_cost_cents`, `compose_input_tokens`, `compose_output_tokens`,
`compose_cost_cents`, `recap_input_tokens`, `recap_output_tokens`, `recap_cost_cents` and the
aggregates `llm_input_tokens`, `llm_output_tokens`, `llm_cost_cents` SHALL continue to exist and
SHALL remain the reported totals for episodes below the attribution boundary. They SHALL NOT be
recomputed from the requests for those episodes, because no request of theirs names an episode.

#### Scenario: An episode's stage totals follow its requests
- **WHEN** an attributable episode's cost breakdown is read
- **THEN** each stage's tokens and cost are the sum of that episode's recorded requests for that
  stage

#### Scenario: A stage that issued several requests
- **WHEN** a stage issued more than one request for an episode
- **THEN** the stage's total covers every one of them and the count of requests shown for that stage
  matches the number recorded

#### Scenario: Per-stage triples written when each stage runs
- **WHEN** an episode is generated end-to-end with all four LLM stages
- **THEN** the scoring, dedup, compose and recap stages each report the tokens and cost of their own
  requests, and the persisted columns still carry those totals for use below the attribution boundary

#### Scenario: Aggregate equals sum of stages
- **WHEN** an episode's aggregate LLM tokens and cost are read
- **THEN** they equal the sum of the four stage totals as reported, whether each was projected or
  read from its column

#### Scenario: Stages that did not run carry zero
- **WHEN** a pipeline run skips a stage (e.g. recap fails and is swallowed)
- **THEN** that stage contributes nothing to the breakdown and the aggregate covers the stages that
  did run

#### Scenario: An episode below the attribution boundary
- **WHEN** an episode generated before requests named an episode is read
- **THEN** its persisted per-stage columns are reported unchanged

## ADDED Requirements

### Requirement: A failed run's cost belongs to the episode that paid it
An episode's reported cost SHALL include the requests of every run it went through, including runs
that failed after issuing requests. A run that was charged for its calls SHALL NOT be excluded
because it did not finish.

Where a failed episode is retried and keeps its identity, the requests of both attempts SHALL be
reported against it. Where a failed episode is re-run as a new episode, each episode SHALL report its
own requests, and the failed episode SHALL keep the cost it incurred rather than transferring it.

#### Scenario: A retry of a failed episode
- **WHEN** an episode fails after issuing requests and is retried under the same episode
- **THEN** its reported cost covers the requests of both the failed attempt and the retry

#### Scenario: A re-run as a new episode
- **WHEN** a failed episode is re-run as a new episode
- **THEN** the failed episode still reports the cost of the requests it issued, and the new episode
  reports only its own

#### Scenario: A failed episode's cost is visible
- **WHEN** costs are reported across episodes
- **THEN** an episode that never reached a finished state is included with what it cost rather than
  omitted

### Requirement: Every candidate an episode scored is recorded
An episode SHALL record one row per article it scored as a candidate, and writing those rows SHALL
NOT fail the episode. They are what the score stage is read through: both the dropped-candidate
breakdown and the check that an episode's scoring is fully attributed count against them, so an
episode that records none reports a score stage it cannot account for.

An article recorded twice for one episode SHALL leave the first outcome standing rather than raising,
on the same terms as the episode-article link: the same fact stated twice is not a reason to fail a
generation.

#### Scenario: A whole run's candidates are written
- **WHEN** an episode scores several hundred candidate articles and records them
- **THEN** every one is recorded and the episode proceeds

#### Scenario: A candidate recorded twice
- **WHEN** the same article is recorded twice as a candidate for one episode
- **THEN** the episode keeps the outcome recorded first and does not fail

### Requirement: Attribution completeness gates the projection
An episode's cost SHALL be projected from its recorded requests only where those requests account for
the whole of what the episode was charged for. Where a stage's requests are known to be incompletely
attributed, the episode SHALL report its persisted columns instead, because a projection over a
partial log understates the cost rather than merely failing to improve it.

The boundary SHALL be derived from the recorded data rather than configured, so there is no
deploy-time constant to keep correct.

#### Scenario: An episode whose scoring requests are fully attributed
- **WHEN** every article an episode was charged for has its scoring request attributed to it
- **THEN** the episode's score stage is projected from those requests

#### Scenario: An episode whose scoring requests are partly unattributed
- **WHEN** an episode was charged for articles whose scoring requests cannot be attributed
- **THEN** the episode reports its persisted score columns rather than a projection that omits them

#### Scenario: The boundary moves as records are backfilled
- **WHEN** historical records gain the article they scored
- **THEN** the episodes that become fully attributed are projected without any configuration change

### Requirement: The cost breakdown and the request log agree
The number of requests a stage is reported to have made, and the cost reported for that stage, SHALL
be derived from the same records wherever an episode is projected. A reader SHALL NOT be able to
observe one count of a stage's requests in the latency view and a different one in the cost
breakdown for the same episode.

#### Scenario: A stage that ran twice across two attempts
- **WHEN** an episode's dedup stage ran once in a failed attempt and once in a retry
- **THEN** both the latency view and the cost breakdown report two dedup requests for that episode

#### Scenario: Cost source reflects the requests counted
- **WHEN** an episode's reported cost is projected from its requests
- **THEN** the source it reports is derived from the sources of exactly those requests
