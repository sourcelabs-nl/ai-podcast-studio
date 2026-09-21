# Capability: Jev Decisions

## Purpose

A client for TypeSafe's Jev model, reached through OpenRouter's decisions endpoint, which answers typed closed questions about supplied state with constrained output instead of generated text.

## Requirements

### Requirement: The system calls Jev over its own HTTP client, not through Spring AI

The system SHALL provide a `JevClient` that posts to OpenRouter's decisions endpoint over `RestClient`. It SHALL NOT be built on `ChatClientFactory`, `CachingChatModel` or `OpenAiChatModel`.

Jev is not a chat model. It is absent from OpenRouter's `/api/v1/models` listing and is not served by `/api/v1/chat/completions`, so no Spring AI `ChatModel` can reach it. It accepts a `state` object and a record of typed `questions`, and returns one constrained answer per question together with a `usage` object carrying the provider's own reported cost.

#### Scenario: A request carries state and a questions record
- **WHEN** the client asks one or more questions about some state
- **THEN** the request body carries `model`, a `state` object and a `questions` record keyed by question name

#### Scenario: Several questions share one state
- **WHEN** the client asks N questions about the same state
- **THEN** they travel in a single request against a single `state`, not N requests

### Requirement: A Jev failure never reaches the caller as an exception

`JevClient` SHALL return an empty answer set, with a logged warning, when the endpoint is unreachable, returns a non-success status, times out, answers unparseably, or no OpenRouter credential is configured. It SHALL NOT throw.

The decisions endpoint is alpha and served by a single provider, with no OpenRouter routing fallback behind it, so its availability is strictly worse than that of the chat models the pipeline depends on. Every caller is therefore required to have a defined behaviour without an answer, and that is only enforceable if the client cannot fail loudly.

#### Scenario: A transport failure yields no answers
- **WHEN** the decisions endpoint times out or refuses the connection
- **THEN** the client logs a warning and returns an empty answer set

#### Scenario: A rejected request yields no answers
- **WHEN** the endpoint answers with a non-success status
- **THEN** the client logs a warning naming the status and returns an empty answer set

#### Scenario: A missing credential yields no answers
- **WHEN** no OpenRouter API key is configured for the user
- **THEN** the client logs a warning and returns an empty answer set without making a request

### Requirement: A transient Jev failure is retried before the caller sees nothing

`JevClient` SHALL retry a request that fails with HTTP 429, any 5xx, TypeSafe's own `529 system_overloaded`, or an I/O fault. It SHALL NOT retry a request the endpoint rejected, such as `400 max_tokens_exceeded` for an oversized batch or a bad credential, since those fail identically on every attempt. Exhausting the attempts SHALL return an empty answer set, not raise.

A retry is the only fallback this endpoint has: it is served by a single provider, and OpenRouter does no routing around it. Its availability makes that matter. In one morning it answered three consecutive requests with `503 no healthy upstream` and both chunks of a live pipeline run with `529 system_overloaded`, while serving healthy requests in under a second either side of both.

The backoff SHALL be short enough that the gate cannot hold up the stage it serves. The gate answers in about 1.6 seconds and the dedup stage runs for 12 to 36, so the retry budget is sized in hundreds of milliseconds rather than the seconds the other external-API retries use.

#### Scenario: An overloaded endpoint is retried and succeeds
- **WHEN** the first attempt returns `529 system_overloaded` and the second succeeds
- **THEN** the caller receives the second attempt's answers

#### Scenario: Exhausted retries yield no answers
- **WHEN** every attempt fails with a transient status
- **THEN** the client returns an empty answer set with no cost, and does not raise

#### Scenario: A rejected request is not retried
- **WHEN** a request fails with `400 max_tokens_exceeded`
- **THEN** exactly one request is made and the client returns no answers

### Requirement: The client reuses the configured OpenRouter credential

`JevClient` SHALL resolve its API key through `UserProviderConfigService` for the `openrouter` provider under the LLM credential category, the same credential the chat stages use. It SHALL NOT introduce a separate provider entry or a separate key for the user to configure.

The endpoint's host and path are a property of OpenRouter rather than of the stored provider configuration, whose base URL points at the chat API, so the decisions URL SHALL come from application configuration rather than being derived from that base URL by string surgery.

#### Scenario: The existing OpenRouter key is used
- **WHEN** the user has an OpenRouter key configured for the LLM category
- **THEN** the client authenticates with that key

### Requirement: A score question's criteria are ordered rubric levels

When the client exposes a `score` question, its criteria SHALL be documented and used as ordered levels from lowest to highest, and the returned value SHALL be normalised by dividing by one less than the number of levels.

Jev's `score` answer is the expected position along the supplied criteria, not a degree to which independent conditions hold, which is why the answer carries a `legend` naming each level. Supplying independent conditions instead inverts the result: over the same 40 articles, a set of independent conditions correlated with the scoring model at Spearman -0.61 where a four-level rubric reached +0.71.

#### Scenario: A rubric score is normalised
- **WHEN** a `score` question supplies four ordered levels and Jev answers 1.5
- **THEN** the normalised value is 0.5

### Requirement: The reported cost of a Jev call is available to its caller

Every answer set SHALL carry the input token count and the provider-reported cost from the response's own `usage` object, so a caller can account for the call.

Nothing about a Jev call passes through `CachingChatModel` or `CostEstimator`, so a call whose cost is not returned to its caller is a call that disappears from the pipeline's cost reporting entirely.

#### Scenario: Usage travels with the answers
- **WHEN** a request succeeds
- **THEN** the returned answer set carries the input tokens and the reported cost in USD

#### Scenario: A failed call reports no cost
- **WHEN** a request fails and the empty answer set is returned
- **THEN** it reports no cost rather than a zero that would be indistinguishable from a free call
