## MODIFIED Requirements

### Requirement: One record per LLM request
The system SHALL record one row for every LLM request it issues, capturing the stage, the provider,
the model, the elapsed wall-clock time of the request, the input and output token counts, the time
the request started, the episode the request was issued for where there is one, and the article the
request was issued for where there is one.

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

The episode and the article SHALL be supplied together as one attribution value rather than as
separate positional arguments threaded through the chat client, so that a caller naming neither, one
or both reads the same at every call site.

#### Scenario: A request issued for one article
- **WHEN** the scoring stage issues a request for an article
- **THEN** the record names that article

#### Scenario: A request that belongs to no article
- **WHEN** the compose stage issues its request
- **THEN** the record names the episode and no article

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

### Requirement: An episode's individual requests are readable
The system SHALL expose the individual recorded requests of one named episode through the API,
reporting for each its stage, model, start time, duration, outcome and whether it was served from
cache.

At an episode's volume the individual requests are what answers the question the percentiles are
asked for: which request was slow, in which stage, and whether it succeeded.

Cached and failed requests are included here, unlike in the percentiles, because the list describes
what the episode did rather than what the provider's latency was.

An episode's requests SHALL include the requests issued for the articles it recorded as candidates,
even though those requests name no episode of their own. An article is scored when it arrives, long
before the episode exists, so a list gathered by episode alone reports a scoring stage that issued
nothing.

#### Scenario: Reading an episode's requests
- **WHEN** the requests of a named episode are requested
- **THEN** each of that episode's recorded requests is reported individually with its stage, model,
  start time, duration, outcome and cache status

#### Scenario: Scoring requests reach the episode that used them
- **WHEN** the requests of an episode with 188 recorded candidates are requested
- **THEN** the scoring requests issued for those 188 articles are reported, including those for the
  candidates that did not reach the script

#### Scenario: A scoring request reaches one episode only
- **WHEN** an article was a candidate for exactly one episode
- **THEN** the request that scored it appears in that episode's requests and in no other's

#### Scenario: An episode recorded before attribution existed
- **WHEN** the requests of an episode that was generated before requests carried an episode are
  requested
- **THEN** the response reports no requests, and does not fall back to unattributed rows
