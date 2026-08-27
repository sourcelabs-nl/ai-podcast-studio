## ADDED Requirements

### Requirement: External API retries use Resilience4j

Retries around external LLM and TTS APIs SHALL use named Resilience4j `Retry` instances obtained from the application's `RetryRegistry`, configured in `application.yaml` under `resilience4j.retry.instances`, rather than hand-written attempt loops.

This SHALL cover the article scoring stage (`article-scoring`), the topic dedup stage (`topic-dedup`), and Inworld speech synthesis (`inworld-tts`). Attempt counts and backoff SHALL be configuration, not code, and SHALL derive from a shared `external-api` base config so the same policy is not restated per call site.

Call sites SHALL use the `resilience4j-kotlin` `executeSuspendFunction` API rather than the `@Retry` AOP annotation, which does not compose with Kotlin `suspend` functions.

#### Scenario: A failing LLM call is retried
- **WHEN** the topic dedup call fails with a parse error and `max-attempts` is 3
- **THEN** the call is attempted 3 times before the failure propagates

#### Scenario: Retry policy is configuration
- **WHEN** an operator changes `resilience4j.retry.configs.external-api.max-attempts`
- **THEN** every stage deriving from that base config retries accordingly, with no code change

#### Scenario: Exhausted retries propagate the failure
- **WHEN** every attempt of the dedup call fails
- **THEN** the exception propagates and the episode fails, rather than composing un-deduped articles

### Requirement: Only transient Inworld faults are retried

The `inworld-tts` retry SHALL list its retryable exceptions explicitly: a rate limit, an I/O failure (connection reset, timeout) and an upstream 5xx. Any other failure SHALL propagate on the first attempt.

#### Scenario: Rate limit is retried
- **WHEN** Inworld responds with a rate limit and the next attempt succeeds
- **THEN** the synthesis returns successfully

#### Scenario: Upstream 5xx is retried
- **WHEN** Inworld responds with HTTP 503 and the next attempt succeeds
- **THEN** the synthesis returns successfully

#### Scenario: Retries are exhausted
- **WHEN** Inworld rate limits every attempt
- **THEN** the rate limit exception propagates after `max-attempts` attempts

#### Scenario: A non-retryable failure fails immediately
- **WHEN** Inworld rejects a request with a non-transient error
- **THEN** the failure propagates without a second attempt

### Requirement: Retry must not re-send an identical scoring prompt

The article scoring stage SHALL track its attempt number and escalate the prompt on each retry with a correction instructing the model to return raw JSON only.

A byte-identical retry would be served from `CachingChatModel`, which keys on prompt text: an unparseable answer would be replayed from cache and fail identically on every attempt, and because a failed article keeps a null `relevanceScore` it would be retried by every later pipeline run.

#### Scenario: Retry escalates the prompt
- **WHEN** the first scoring attempt returns unparseable output
- **THEN** the second attempt sends the prompt with a "raw JSON only" correction appended

#### Scenario: First attempt is unmodified
- **WHEN** an article is scored on the first attempt
- **THEN** the prompt carries no correction text

### Requirement: Retry and breaker events are logged

Retry attempts and circuit breaker state transitions SHALL be logged. Listeners SHALL be attached through `RegistryEventConsumer` beans so that an instance created on demand (such as one breaker per source host) is instrumented exactly once, however many call sites share it.

#### Scenario: A retry is logged
- **WHEN** an external API call is retried
- **THEN** a log line records the retry instance name, the attempt number and the failure message

#### Scenario: A breaker transition is logged
- **WHEN** a host's circuit breaker moves from closed to open
- **THEN** a log line records the breaker name and the state transition

### Requirement: Validation-feedback retries remain advisor-based

`RoleTagValidationAdvisor` SHALL NOT be converted to a Resilience4j `Retry`. It mutates the request with the specific validation error before each attempt, which a retry policy cannot express, and it implements Spring AI's own `CallAdvisor` extension point for that pattern.

#### Scenario: Invalid speaker tags feed the error back to the model
- **WHEN** the compose model emits a speaker tag outside the allowed roles
- **THEN** the next attempt receives the prompt augmented with the specific invalid tags, rather than an unchanged prompt
