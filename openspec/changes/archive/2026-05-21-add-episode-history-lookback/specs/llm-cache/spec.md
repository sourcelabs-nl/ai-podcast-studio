## ADDED Requirements

### Requirement: Cache correctly handles tool-augmented compose calls

`CachingChatModel` SHALL cache the final assistant `ChatResponse` (after any Spring AI tool-call loop has resolved) keyed on `(model, user-prompt-hash)`. Intermediate tool-call requests and responses MUST NOT be persisted as independent cache entries.

A second invocation with the same `(model, user-prompt)` MUST return a cache hit without issuing any new outbound tool invocations.

#### Scenario: Cache hit replays without tool invocations

- **WHEN** the compose stage runs twice with the identical model and user prompt with `searchPastEpisodes` registered
- **THEN** the second run returns the cached `ChatResponse` and records zero `searchPastEpisodes` invocations
