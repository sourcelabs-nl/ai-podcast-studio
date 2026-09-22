## MODIFIED Requirements

### Requirement: One record per LLM request
The system SHALL record one row for every LLM request it issues, capturing the stage, the provider,
the model, the elapsed wall-clock time of the request, the input and output token counts, the time
the request started, the resolved cost of the request and the source that cost came from, and the
episode the request was issued for where there is one.

The cost SHALL be resolved and written with the record itself, at the moment the request completes,
and SHALL NOT depend on the stage that issued it reaching its end. A request that was answered was
paid for, so its cost SHALL survive the failure of anything that happens after it.

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

#### Scenario: A request whose stage later fails
- **WHEN** a stage issues a request, receives a response, and the stage then fails before persisting
  its results
- **THEN** the record and its resolved cost remain, naming the episode the request was issued for

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

## ADDED Requirements

### Requirement: A record states whether its cost was reported or estimated
Each record SHALL carry the source of its cost with the same values the episode already uses: a
provider-reported cost, a cache replay of one, a cost computed from the configured rates, or none
determinable. A record whose cost could not be determined SHALL say so rather than carrying a zero,
because a zero is indistinguishable from a call that genuinely cost nothing.

#### Scenario: A provider that reports its cost
- **WHEN** a request is answered by a provider that reports the cost of the call
- **THEN** the record carries that value and names it as reported

#### Scenario: A provider that reports nothing
- **WHEN** a request is answered by a provider that reports no cost and the model has configured
  rates
- **THEN** the record carries the cost computed from those rates and names it as estimated

#### Scenario: A model with neither
- **WHEN** a request is answered by a provider that reports no cost and the model has no configured
  rates
- **THEN** the record carries no cost and names its source as undetermined

### Requirement: A scoring request names the article it scored
A request issued to score one article SHALL name that article, so the cost of scoring can be
attributed to the episode that the article stood as a candidate for. Scoring happens when an article
is polled rather than while an episode runs, so the request cannot be attributed to an episode at the
time it is made; the article is the only link back.

Records written before requests named an article SHALL be backfilled where the article they scored
is identifiable, and left unattributed where it is not.

#### Scenario: An article is scored
- **WHEN** the scoring stage issues a request for one article
- **THEN** the record names that article

#### Scenario: The episode that paid for the scoring
- **WHEN** an episode's recorded candidates are read together with the scoring requests naming those
  articles
- **THEN** every article the episode was charged for has its scoring request attributed to it

#### Scenario: A historical record whose article cannot be identified
- **WHEN** a record written before requests named an article cannot be matched to one
- **THEN** it stays unattributed rather than being assigned to an arbitrary article
