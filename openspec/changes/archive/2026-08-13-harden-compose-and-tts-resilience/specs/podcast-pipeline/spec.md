## ADDED Requirements

### Requirement: Compose input is capped to the highest-relevance articles
The pipeline SHALL cap the number of articles fed into a single compose request to a configurable maximum (`compose.max-articles`, default 40), keeping the highest `relevance_score` articles and dropping the rest. The cap SHALL be enforced at the shared compose chokepoint so every entry path is bounded, including retry-from-compose which reloads previously persisted articles and skips dedup. Dropped articles SHALL remain unprocessed (not linked to the episode) so they stay eligible and are cleared by the existing age gate once the next episode publishes.

#### Scenario: More surviving articles than the cap
- **WHEN** 62 articles survive scoring and dedup and `compose.max-articles` is 40
- **THEN** only the 40 highest-relevance articles are composed, and the cap is logged

#### Scenario: Fewer surviving articles than the cap
- **WHEN** 12 articles survive scoring and dedup and `compose.max-articles` is 40
- **THEN** all 12 articles are composed and no cap is applied

#### Scenario: Retry from compose is also bounded
- **WHEN** a failed episode with 62 persisted articles is retried and resumes from compose (skipping dedup)
- **THEN** the compose input is still capped to the highest-relevance 40 articles

### Requirement: Compose LLM request timeout
The compose `ChatClient` SHALL use a request timeout of 20 minutes so a large compose with deep-dive web research and history tool calls has headroom to complete. The dedup and filter stages, which bound their own output via `maxTokens`, are unaffected.

#### Scenario: Long compose within the timeout
- **WHEN** a compose request over a large article set runs for more than 10 minutes but less than 20 minutes
- **THEN** the request completes successfully rather than timing out
