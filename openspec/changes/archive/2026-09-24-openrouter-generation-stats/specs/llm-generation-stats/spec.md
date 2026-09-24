## ADDED Requirements

### Requirement: OpenRouter generation stats are fetched after each request

For every successful, non-cached LLM request served through OpenRouter, the system SHALL look up
that request's generation stats from OpenRouter's generation endpoint using the generation id the
response carried, and store them on the request's record. The lookup SHALL happen after the request
has returned, SHALL NOT delay or change the outcome of the call it describes, and SHALL retry while
the endpoint reports the generation as not yet available, giving up after a bounded period.

The stored stats SHALL be: time until the first answer token (ms, OpenRouter's `latency`, which
follows the model's reasoning), total generation time (ms, spanning the whole request), native
completion tokens, native reasoning tokens, finish reason, the name of the provider that served the
request, and the list of upstream attempts, each with provider name, HTTP status and latency (ms,
the time until that endpoint started responding).

Each request's time SHALL be split into phases: startup (the sum of all attempts' latencies),
reasoning (time until the first answer token minus startup), writing (total generation time minus
time until the first answer token), and output tokens per second (native completion tokens over
total generation time minus startup). A phase SHALL be null when a value it needs is missing.

#### Scenario: Stats become available after a delay
- **WHEN** a compose request completes and the generation endpoint answers 404 for the first
  lookups and returns stats 15 seconds later
- **THEN** the request's record holds time until the first answer token, generation time, native
  token counts, finish reason and attempts from that response

#### Scenario: Phases of a reasoning compose request
- **WHEN** a compose request reports first answer token at 130,538 ms, generation time 159,954 ms,
  14,020 native completion tokens and one attempt with latency 1,777 ms
- **THEN** its phases are startup 1,777 ms, reasoning 128,761 ms, writing 29,416 ms and about 88.6
  tokens per second

#### Scenario: Stats never become available
- **WHEN** the generation endpoint keeps answering 404 until the retry period ends
- **THEN** the request's record keeps its generation stats empty, the episode is unaffected, and
  a warning is logged

#### Scenario: Request not eligible
- **WHEN** a request is a cache hit, failed, or went to a provider other than OpenRouter
- **THEN** no generation lookup is made and its generation stats stay empty

#### Scenario: Provider fallback
- **WHEN** OpenRouter tried one provider that failed and then served the request from another
- **THEN** the record's attempts list both providers with their statuses and latencies, in order

### Requirement: Experiments report a compose performance breakdown

The experiment comparison SHALL report, for each run, a compose performance breakdown summed over
that run's compose requests: startup, reasoning and writing time, reasoning share (native reasoning
tokens divided by native completion tokens), output tokens per second (native completion tokens
divided by reasoning plus writing time), and the number of upstream attempts beyond the first. Each variant SHALL report the mean of those values over its completed runs. A value SHALL be
null when any of the run's compose requests lacks the stats it is derived from.

#### Scenario: Run with stats
- **WHEN** an experiment run's single compose request has first answer token at 80,000 ms,
  generation time 102,000 ms, 12,000 native completion tokens of which 9,000 reasoning, and one
  attempt with latency 2,000 ms
- **THEN** the run reports startup 2,000 ms, reasoning 78,000 ms, writing 22,000 ms, reasoning share
  0.75, 120 tokens per second and 0 fallback attempts

#### Scenario: Run without stats
- **WHEN** an experiment run's compose request has no generation stats
- **THEN** the run's breakdown values are null and the variant mean excludes that run
