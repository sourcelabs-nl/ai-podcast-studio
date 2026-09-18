## Purpose

Ties each recorded LLM request to the episode it was issued for, so recorded latency and outcomes
can be read for one generation rather than only as a fleet-wide aggregate.

## ADDED Requirements

### Requirement: Requests issued for an episode record that episode
Every LLM request issued while generating or evaluating an episode SHALL record the identity of
that episode on its telemetry row.

This applies to all pipeline stages, not only composition: the episode row is created before
scoring begins, so scoring, dedup, composition and evaluation all have an episode in scope.

#### Scenario: Requests during a generation
- **WHEN** the generation pipeline issues scoring, dedup or composition requests for an episode
- **THEN** each recorded row names that episode

#### Scenario: Evaluation of a finished episode
- **WHEN** the script judge issues requests for an episode that has already been generated
- **THEN** each recorded row names that episode

#### Scenario: A stage that issues several requests
- **WHEN** one composition invocation issues several requests through its tool loop
- **THEN** every one of those rows names the same episode

### Requirement: Requests with no episode record none
An LLM request issued outside the generation or evaluation of a specific episode SHALL record no
episode rather than being attributed to an unrelated one.

Preview and ad-hoc source scoring are real paths with no episode to name. Attribution is therefore
optional on the record, and its absence is a truthful answer rather than missing data.

#### Scenario: Preview and ad-hoc scoring
- **WHEN** a request is issued by a preview run or by scoring sources outside a generation
- **THEN** the recorded row carries no episode, and the request is still recorded in full

#### Scenario: Reading an episode's requests
- **WHEN** an episode's requests are read back
- **THEN** rows carrying no episode are not included in any episode's results

### Requirement: Attribution survives the dispatcher boundary
The episode identity SHALL reach the recorded row by being passed along the call path explicitly.

The composers hand off to another dispatcher before the request is issued, so an identity held in
thread-bound ambient state would not arrive. Correct attribution must not depend on which thread
executes the request.

#### Scenario: A composition request on another dispatcher
- **WHEN** a composition request is issued on a different thread from the one that started the stage
- **THEN** the recorded row still names the episode being generated

### Requirement: Attribution never affects the request it describes
A failure to determine or record the episode SHALL NOT fail, delay or alter the LLM request.

#### Scenario: Recording fails
- **WHEN** writing the telemetry row fails for any reason
- **THEN** the LLM request's own result is returned to the caller unchanged
