## Purpose

Records how long each individual LLM request takes and whether it succeeded, so that per-stage
request timeouts can be sized on measured latency rather than on stage wall-clock, and so that a
slow model or a degrading provider becomes visible before it fails an episode.

## ADDED Requirements

### Requirement: One record per LLM request
The system SHALL record one row for every LLM request it issues, capturing the pipeline stage, the
provider, the model, the elapsed wall-clock time of the request, the input and output token counts,
and the time the request started.

A single pipeline stage MAY issue several requests, and each SHALL be recorded separately, so that
the recorded duration is the latency of one request and never the sum of a stage's requests or the
time spent executing tools between them.

#### Scenario: A request without tool calls
- **WHEN** a stage issues one LLM request and receives a final response
- **THEN** exactly one record is written, whose duration is the elapsed time of that request

#### Scenario: A request that triggers tool execution
- **WHEN** the model responds with tool calls and the system issues a follow-up request carrying the
  tool results
- **THEN** each request is recorded separately, and no record's duration includes the time spent
  executing the tools

#### Scenario: Scoring many articles
- **WHEN** the scoring stage issues one request per article across a batch
- **THEN** one record is written per request, not one for the batch

### Requirement: Cache hits are recorded and distinguishable
A response served from the LLM cache performs no network request. The system SHALL record such a
call, and SHALL mark it as a cache hit so that it can be excluded from latency analysis.

#### Scenario: Response served from cache
- **WHEN** a request is satisfied from the LLM cache without contacting the provider
- **THEN** a record is written marked as a cache hit

#### Scenario: Percentiles exclude cache hits
- **WHEN** latency percentiles are computed
- **THEN** records marked as cache hits are excluded, so that they cannot lower the reported latency
  of real requests

### Requirement: Failed requests are recorded and distinguishable
The system SHALL record a request that fails, marking its outcome as a failure, so that a timeout or
an upstream error is visible rather than silently absent.

Failed requests SHALL be excluded from latency percentiles by default, because a request that hit
its timeout reports the timeout's duration rather than the provider's latency.

#### Scenario: Request times out
- **WHEN** a request exceeds its stage timeout and fails
- **THEN** a record is written with a failure outcome and the elapsed time until the failure

#### Scenario: Provider returns an error
- **WHEN** a request fails with an upstream error
- **THEN** a record is written with a failure outcome

### Requirement: Recording never affects the call it measures
Recording is observability and SHALL NOT change the outcome of the request it observes. If writing a
record fails, the system SHALL log the failure and continue, returning the LLM response to the
caller unchanged.

#### Scenario: Recording fails
- **WHEN** the record cannot be written
- **THEN** the failure is logged and the LLM response is returned to the caller as if recording had
  succeeded

#### Scenario: Recording does not alter existing accounting
- **WHEN** requests are recorded
- **THEN** the existing per-article and per-stage token and cost totals are unchanged

### Requirement: Latency percentiles are readable per stage
The system SHALL expose the recorded latency as percentiles (p50, p90, p95 and p99) grouped by
pipeline stage over a requested time window, together with the number of requests each percentile
was computed from, through the application's API.

Reporting the sample count is required because a percentile over a handful of requests does not
support a timeout decision, and the caller must be able to see that.

#### Scenario: Percentiles over a window
- **WHEN** percentiles are requested for a time window
- **THEN** the response reports p50, p90, p95, p99 and the sample count per stage, computed over
  successful non-cached requests in that window

#### Scenario: A stage with no requests in the window
- **WHEN** a stage issued no qualifying requests in the requested window
- **THEN** the response reports that stage with a zero sample count rather than omitting it or
  reporting a misleading percentile

### Requirement: Records are retained for a bounded period
Because the log grows with every request, the system SHALL delete records older than a configurable
retention period, and SHALL apply the retention automatically rather than on request.

#### Scenario: Records age out
- **WHEN** a record is older than the configured retention period
- **THEN** it is deleted automatically

#### Scenario: Retention period is configurable
- **WHEN** the retention period is changed in configuration
- **THEN** the new period governs which records are deleted, without a code change
