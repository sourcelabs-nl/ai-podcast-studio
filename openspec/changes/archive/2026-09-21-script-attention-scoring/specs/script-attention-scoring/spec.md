## ADDED Requirements

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

### Requirement: The judge has three modes and the third one needs a norm
The judge SHALL run under one of three modes, configured for the application: `OFF`, `ADVISE` and `ENFORCE`.

In `OFF` no judge call is made and no score is produced, so the feature costs nothing when it is not wanted.

In `ADVISE` every generated episode is judged after composition, the score is persisted and reported, and the episode is published regardless of what the score says. This is the mode that produces the baseline the third mode needs, and therefore the default.

Judging SHALL NOT be able to fail an episode that was otherwise produced successfully. The episode exists and is deliverable before it is judged, so a judge that errors is logged and the episode stands.

In `ENFORCE` the score is compared against a configured norm and the run acts on the result. The norm SHALL be an explicit configuration value with no default. While no norm is configured, `ENFORCE` SHALL behave exactly as `ADVISE` and SHALL record, once per run, that it did so and why. A fabricated default norm is forbidden: a threshold nobody derived is indistinguishable in the output from one that was measured, and would reject good episodes with the authority of a number.

The mode SHALL NOT change what the judge returns. A score produced under `ENFORCE` is the same score as one produced under `ADVISE`, so rows from the two modes are comparable.

#### Scenario: Off makes no call
- **WHEN** the mode is `OFF` and an episode is generated
- **THEN** no judge model call is made and no score row is written

#### Scenario: Advise never blocks
- **WHEN** the mode is `ADVISE` and an episode scores poorly
- **THEN** the score is persisted and the episode proceeds unchanged

#### Scenario: Enforce without a norm falls back to advise
- **WHEN** the mode is `ENFORCE` and no norm is configured
- **THEN** the episode proceeds as under `ADVISE` and the run records that the norm was absent

#### Scenario: Enforce with a norm acts on the comparison
- **WHEN** the mode is `ENFORCE`, a norm is configured, and a score falls below it
- **THEN** the run records the shortfall against the norm as the reason

### Requirement: An evaluation run reaches the model on every repetition
An evaluation run SHALL compose with the LLM cache neither read nor written. `CachingChatModel` SHALL take an explicit flag for this, `ChatClientFactory` SHALL expose it on both client-construction entry points, and `ComposeContext` SHALL carry it so every compose path can request it.

The cache key is the model plus the `USER` and `SYSTEM` prompt text and ignores temperature, so without the bypass k repetitions of one prompt variant would be a single model call and k-1 replays of its answer. The spread those repetitions exist to measure would then be zero by construction, and a comparison between two variants would be reporting the cache rather than the model.

Such a run SHALL NOT write to the cache either, so an experiment never displaces the answer a production generation would read.

Regeneration SHALL be the entry point, since it recomposes the articles a past episode already selected, and it SHALL be requestable over HTTP.

#### Scenario: Repetitions each reach the model
- **WHEN** an evaluation run issues the same prompt twice
- **THEN** the underlying model is called twice and the cache is not consulted

#### Scenario: An experiment does not displace production
- **WHEN** an evaluation run completes
- **THEN** nothing is written to the LLM cache

#### Scenario: An ordinary generation still uses the cache
- **WHEN** a generation does not ask for a bypass
- **THEN** the cache is read and written as before

### Requirement: An evaluation run records the conditions it ran under
An evaluation run SHALL persist, against the episode it produced: the hash of the exact compose prompt, the prompt-variety selection, the compose model, the temperature, whether the cache was bypassed, whether a cached completion was nevertheless replayed, and how often each compose tool fired. A row SHALL exist only for a run that asked for a bypass.

The composer is not deterministic, so a difference between two sets of scripts is attributable only when the conditions of every run are known. A substituted model, a changed temperature, a different variety rotation, a replayed answer or a tool that fired in one arm and not the other would each invalidate a comparison without being visible in the scripts.

A failure to record SHALL NOT fail the episode, which is a deliverable in its own right. Runs SHALL be readable over HTTP, per podcast and per episode, so the record is never obtained by querying the database.

#### Scenario: An ordinary generation records nothing
- **WHEN** an episode is generated without a cache bypass
- **THEN** no evaluation run is recorded

#### Scenario: A bypass that did not take effect is visible
- **WHEN** a run asked for a bypass and a cached completion was replayed anyway
- **THEN** the run is recorded with a cache hit, so it is not counted as an independent sample

#### Scenario: Losing the record does not lose the episode
- **WHEN** writing the evaluation run fails
- **THEN** the episode completes and the failure is logged
