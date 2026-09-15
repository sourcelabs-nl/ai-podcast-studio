## ADDED Requirements

### Requirement: Structural metrics are computed without a model
The system SHALL compute, with no LLM call, metrics that are a pure function of an episode script: the number of speaker turns, the turn and word count per role, the word share per role, the median, maximum and over-threshold count of expert turn lengths, and which role owns each `[laugh]` tag.

These metrics SHALL be stable across model versions and SHALL be cheap enough to run over every episode in the archive.

`[laugh]` ownership SHALL be reported as a proxy for humor distribution and never as a humor count, since a joke carrying no laugh tag is invisible to it.

#### Scenario: Metrics computed without a model
- **WHEN** `ScriptMetrics` is given a script
- **THEN** it returns the counts and distributions with no LLM call

#### Scenario: A script with one role only
- **WHEN** a script contains no expert turns
- **THEN** the expert statistics are empty rather than an error

#### Scenario: A script with no turns
- **WHEN** a script carries no speaker tags
- **THEN** the metrics are empty rather than an error

### Requirement: Backchannel candidates are reported by shape
The system SHALL report backchannel candidates: a short interviewer turn appearing between two expert turns, each with its turn index.

They SHALL be reported as candidates rather than as confirmed backchannels, since a genuinely short question has the same shape and only the text distinguishes the two.

#### Scenario: The shape is present
- **WHEN** an expert turn is followed by a three-word interviewer turn and another expert turn
- **THEN** that interviewer turn is reported as a backchannel candidate with its index

#### Scenario: A short question between two expert turns
- **WHEN** the short interviewer turn is a question
- **THEN** it is still reported as a candidate, since the metric does not judge intent

### Requirement: Consecutive same-speaker turns are located
The system SHALL report each run of consecutive turns by one speaker, with the role and the turn indices involved.

They SHALL NOT be reported as errors: a resumed turn after a backchannel is legitimate and a continued point is not, and only the text distinguishes them.

#### Scenario: A resumed expert turn
- **WHEN** two expert turns are separated by a backchannel
- **THEN** the run is located and reported without being called a defect

### Requirement: Metrics are read through the API and not stored
Metrics SHALL be available over REST for a single episode and for a range of episodes, and the controller SHALL validate input and delegate without holding logic of its own.

Metrics SHALL NOT be persisted. They are recomputed from the stored script on each request, so they cannot disagree with the script they describe.

#### Scenario: Reading the archive
- **WHEN** a client asks for the metrics of a range of episodes
- **THEN** the API returns them without the client touching the database

#### Scenario: A script is edited
- **WHEN** an episode's script changes and its metrics are requested again
- **THEN** the returned metrics describe the current script
