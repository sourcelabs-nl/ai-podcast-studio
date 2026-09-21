## ADDED Requirements

### Requirement: A Jev call is recorded as an LLM request
Every request the system issues to the decisions endpoint SHALL be recorded in the same log as the
system's other LLM requests, carrying the stage that asked, the model, the elapsed time, the input
tokens the endpoint reported, the cost it reported, and whether it succeeded.

Without this the endpoint is billed for and waited on while being absent from every view that
reports what the system spent and how long it took, and its failures are visible only in the
application log.

Each attempt SHALL be recorded, so that a retried transient failure is visible as a failure rather
than absorbed into the attempt that eventually succeeded.

A failure to record SHALL NOT change what the call returns, in keeping with the requirement that a
Jev failure never reaches the caller as an exception.

#### Scenario: A successful decision call
- **WHEN** the endpoint answers a batch of questions
- **THEN** one record is written for it, marked successful, carrying the reported input tokens and
  the reported cost

#### Scenario: A decision call that is retried
- **WHEN** the endpoint returns a transient status and the request is retried
- **THEN** one record is written per attempt, the failed attempts marked as failures carrying the
  kind of failure

#### Scenario: A decision call that times out
- **WHEN** the endpoint does not answer within the configured timeout
- **THEN** the record is marked as a failure and identified as a timeout, so that it counts as
  latency rather than as a fast rejection

#### Scenario: No credential is configured
- **WHEN** no credential is available and no request is issued
- **THEN** no record is written, because nothing was asked of the provider

### Requirement: A Jev call names the episode it was issued for
Where the system asks Jev a question in the course of producing an episode, the recorded request
SHALL name that episode, so that the call appears in the list of requests that episode issued.

#### Scenario: The dedup stage asks its gate
- **WHEN** the already-covered gate is evaluated while generating an episode
- **THEN** the recorded request names that episode

#### Scenario: A question asked outside an episode
- **WHEN** a Jev question is asked by a path with no episode in scope
- **THEN** the record is written in full with no episode named
