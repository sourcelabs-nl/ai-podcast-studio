# llm-call-telemetry Specification

## Purpose
Records how long each individual LLM request takes and whether it succeeded, so that per-stage
request timeouts can be sized on measured latency rather than on stage wall-clock, and so that a
slow model or a degrading provider becomes visible before it fails an episode.

## Requirements

### Requirement: One record per LLM request
The system SHALL record one row for every LLM request it issues, capturing the stage, the provider,
the model, the elapsed wall-clock time of the request, the input and output token counts, the time
the request started, and the episode the request was issued for where there is one.

A single stage MAY issue several requests, and each SHALL be recorded separately, so that the
recorded duration is the latency of one request and never the sum of a stage's requests or the time
spent executing tools between them.

Recording SHALL NOT depend on how the request was issued. A request sent outside the shared chat
client, against a provider API that returns something other than a chat completion, SHALL be
recorded on the same terms as one sent through it, because a request that is invisible in this log
is invisible wherever the log is read.

Where a request is retried, each attempt SHALL be recorded separately. A retried attempt really
reached the provider and really consumed time, and collapsing the attempts would hide both the
failure and how often it repeats.

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

#### Scenario: A request issued outside any episode
- **WHEN** a request is issued by a path that has no episode in scope
- **THEN** the record is written in full with no episode named

#### Scenario: A request issued outside the shared chat client
- **WHEN** a stage issues a request against a provider API that is not a chat completion
- **THEN** a record is written for it carrying the same fields as any other request, including the
  cost the provider reported for it

#### Scenario: A request that is retried after a transient failure
- **WHEN** a request fails with a transient status and is retried
- **THEN** each attempt is recorded, the failed ones marked as failures and the successful one as a
  success, and the interval waited between attempts appears in no record's duration

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
The system SHALL record a request that failed, marked as a failure and carrying what kind of failure
it was, so that a stage's failure rate can be read from the same log as its latency.

A failure that the system classifies as a timeout SHALL be distinguishable from every other failure
by a stable value rather than by the name of an implementation type, because the distinction decides
whether the row is counted as latency.

Failed requests SHALL be excluded from latency percentiles by default, because a request that hit
its timeout reports the timeout's duration rather than the provider's latency.

#### Scenario: Request times out
- **WHEN** a request does not complete within the timeout it was issued with
- **THEN** a record is written marked as a failure and identified as a timeout

#### Scenario: Provider returns an error
- **WHEN** a request fails against the provider
- **THEN** a record is written marked as a failure, carrying the kind of failure, with zero token
  counts

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
stage, together with the number of requests each percentile was computed from, through the
application's API.

The caller SHALL be able to request either a time window across all episodes or the requests of one
episode.

The set of stages reported SHALL be the set of stages that issue recorded requests, and SHALL NOT be
limited to the stages of the episode-generating pipeline. A stage that issues requests but is absent
from the report is indistinguishable from one that issued none, which is the condition this
requirement exists to prevent. Each reported stage SHALL carry the request timeout its own requests
are issued with, which for a stage outside the pipeline is that stage's own configured timeout.

Every reported stage SHALL appear even when it issued nothing in scope, with a zero sample count.

#### Scenario: Percentiles over a window
- **WHEN** percentiles are requested for a time window
- **THEN** the response reports p50, p90, p95, p99 and the sample count per stage, computed over the
  qualifying requests in that window

#### Scenario: A stage with no requests in the window
- **WHEN** a stage issued no qualifying requests in the requested window
- **THEN** the response reports that stage with a zero sample count rather than omitting it or
  reporting a misleading percentile

#### Scenario: A stage outside the episode-generating pipeline
- **WHEN** a stage that is not part of the episode-generating pipeline has issued requests
- **THEN** the response reports that stage alongside the pipeline stages, against the timeout its
  own requests are issued with

### Requirement: Records are retained for a bounded period
Because the log grows with every request, the system SHALL delete records older than a configurable
retention period, and SHALL apply the retention automatically rather than on request.

#### Scenario: Records age out
- **WHEN** a record is older than the configured retention period
- **THEN** it is deleted automatically

#### Scenario: Retention period is configurable
- **WHEN** the retention period is changed in configuration
- **THEN** the new period governs which records are deleted, without a code change

### Requirement: Percentiles cover successful requests and timeouts
Latency percentiles SHALL be computed over requests that succeeded and over requests that timed out,
and SHALL exclude cache hits and every other failure.

A cache hit performed no request. A request that failed quickly, such as a rejection or a provider
overload, reports how fast the provider refused rather than how long it takes to answer, and
counting it would pull the percentiles down exactly when the provider is unhealthy. A timeout is the
opposite case: it is a real request that was genuinely too slow, and excluding it hides the slowest
requests from the percentiles meant to show them.

A consequence the report SHALL NOT hide is that a saturated stage reads as a p99 at or near its
configured timeout, because that is the longest a request is permitted to take.

#### Scenario: A stage with fast failures in the window
- **WHEN** a stage's requests in the window include failures that did not time out
- **THEN** those requests are excluded from the percentiles and from the sample count

#### Scenario: A stage with timeouts in the window
- **WHEN** a stage's requests in the window include requests that timed out
- **THEN** those requests are included in the percentiles and in the sample count

#### Scenario: Reading a stage that is hitting its ceiling
- **WHEN** most of a stage's requests in the window timed out
- **THEN** its reported percentiles approach the timeout those requests were issued with, rather
  than reporting only the few requests that returned in time
