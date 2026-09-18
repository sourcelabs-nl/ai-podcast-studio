## MODIFIED Requirements

### Requirement: One record per LLM request
The system SHALL record one row for every LLM request it issues, capturing the pipeline stage, the
provider, the model, the elapsed wall-clock time of the request, the input and output token counts,
the time the request started, and the episode the request was issued for where there is one.

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

#### Scenario: A request issued outside any episode
- **WHEN** a request is issued by a path that has no episode in scope
- **THEN** the record is written in full with no episode named

### Requirement: Latency percentiles are readable per stage
The system SHALL expose the recorded latency as percentiles (p50, p90, p95 and p99) grouped by
pipeline stage, together with the number of requests each percentile was computed from, through the
application's API.

The caller SHALL be able to request either a time window across all episodes or the requests of one
named episode. An episode is a bounded set of requests rather than a period, so when an episode is
named no time window applies.

Reporting the sample count is required because a percentile over a handful of requests does not
support a timeout decision, and the caller must be able to see that. This matters more for an
episode than for a window: an episode's percentiles rest on a few requests per stage, where p99 is
the slowest observed request rather than a tail estimate.

#### Scenario: Percentiles over a window
- **WHEN** percentiles are requested for a time window with no episode named
- **THEN** the response reports p50, p90, p95, p99 and the sample count per stage, computed over
  successful non-cached requests in that window across all episodes

#### Scenario: Percentiles for one episode
- **WHEN** percentiles are requested for a named episode
- **THEN** the response reports p50, p90, p95, p99 and the sample count per stage over that
  episode's successful non-cached requests only, regardless of when they were issued

#### Scenario: A stage with no requests in the window
- **WHEN** a stage issued no qualifying requests in the requested window or for the requested episode
- **THEN** the response reports that stage with a zero sample count rather than omitting it or
  reporting a misleading percentile

## ADDED Requirements

### Requirement: An episode's individual requests are readable
The system SHALL expose the individual recorded requests of one named episode through the API,
reporting for each its stage, model, start time, duration, outcome and whether it was served from
cache.

At an episode's volume the individual requests are what answers the question the percentiles are
asked for: which request was slow, in which stage, and whether it succeeded.

Cached and failed requests are included here, unlike in the percentiles, because the list describes
what the episode did rather than what the provider's latency was.

#### Scenario: Reading an episode's requests
- **WHEN** the requests of a named episode are requested
- **THEN** each of that episode's recorded requests is reported individually with its stage, model,
  start time, duration, outcome and cache status

#### Scenario: An episode recorded before attribution existed
- **WHEN** the requests of an episode that was generated before requests carried an episode are
  requested
- **THEN** the response reports no requests, and does not fall back to unattributed rows
