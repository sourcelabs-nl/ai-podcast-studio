## MODIFIED Requirements

### Requirement: Score, summarize, and filter stage
The system SHALL process each article through a single LLM call that performs scoring and summarization simultaneously. Articles SHALL be processed concurrently using coroutines with `supervisorScope` for fault isolation — a failure in one article's LLM call SHALL NOT cancel or affect processing of other articles.

The system SHALL limit the number of concurrent LLM requests using a configurable concurrency window (default: 10). All articles are dispatched as coroutines, but at most N coroutines SHALL execute their LLM call simultaneously. When a request completes, the next waiting coroutine SHALL proceed immediately (sliding window, not batch-based).

The system SHALL retry transient LLM failures with exponential backoff before giving up on an article. The maximum number of attempts SHALL be configurable (default: 3, where 1 means no retry). The backoff delay SHALL double on each retry (1s, 2s, 4s, ...). Each retry attempt SHALL be logged at WARN level with the attempt number, article title, and error message. Only after all retry attempts are exhausted SHALL the article be excluded from the result list and logged as an ERROR.

Every attempt SHALL send a prompt distinct from every other attempt for the same article. The first attempt SHALL send the prompt unchanged; each subsequent attempt SHALL append a correction that names the attempt number, states that the previous response could not be parsed as JSON, and asks for the raw JSON object with no reasoning, commentary, or markdown code fences.

Sending the byte-identical prompt on a retry cannot succeed. The `llm-cache` capability keys entries on prompt text and rejects only blank completions, so a model that answers with prose instead of JSON has that unparseable answer cached: every retry replays it from cache in milliseconds, and the exponential backoff accomplishes nothing. Because a failed article keeps a null `relevanceScore`, it is re-queued by the unscored-article query on every later pipeline run, so the article can never be scored. Naming the attempt makes each retry a real model call, and the correction itself addresses the observed failure mode.

The LLM prompt SHALL include the podcast's topic, the full article content, and instructions to: (1) assign a relevance score of 0-10, (2) summarize the relevant content only. The prompt SHALL request a JSON response with the structure: `{ "relevanceScore": <int>, "summary": "<text>" }`. The prompt SHALL instruct the LLM to preserve attribution in the summary and focus only on content relevant to the podcast's topic. The system SHALL persist the `relevanceScore` and `summary` on the article immediately after the call. Token usage SHALL be extracted and persisted on the article.

#### Scenario: First attempt sends the prompt unchanged
- **WHEN** an article's first scoring attempt is made
- **THEN** the prompt carries no correction text

#### Scenario: Retry appends a JSON-only correction
- **WHEN** an article's first attempt fails and a second is made
- **THEN** the second prompt is the original prompt followed by a correction naming attempt 2 and asking for raw JSON only

#### Scenario: Every attempt sends a distinct prompt
- **WHEN** an article's LLM call fails on all 3 attempts (default max-retries)
- **THEN** the three prompts sent are all different from one another, so no attempt can be served a cached response from an earlier attempt

#### Scenario: A cached unparseable response does not permanently block an article
- **WHEN** an article's first attempt is served a cached response that cannot be parsed as JSON, and the second attempt's model call returns valid JSON
- **THEN** the article is scored and persisted, so it is not re-queued by the unscored-article query on the next pipeline run
