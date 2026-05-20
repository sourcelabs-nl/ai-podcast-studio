## ADDED Requirements

### Requirement: Subtopic classification in score+summarize stage
When the podcast has a non-empty `subtopics` map, the Stage 1 LLM prompt SHALL include the list of subtopic names (verbatim, with weights omitted from the prompt to avoid biasing classification) and SHALL request a JSON response with the structure `{ "relevanceScore": <int>, "subtopic": <string|null>, "summary": "<text>" }`. The system SHALL validate the LLM-returned `subtopic` against the configured subtopic names: any value not matching an entry in the podcast's `subtopics` map (case-insensitive comparison) SHALL be normalized to `null`. The validated value SHALL be persisted on the article (`articles.subtopic` column). When the podcast has no subtopics configured, the prompt and response schema SHALL remain identical to the pre-feature behavior (`{relevanceScore, summary}` only) and the `subtopic` column SHALL be left null.

The prompt SHALL instruct the LLM to choose the subtopic that best matches the article's primary focus, returning `null` only when no listed subtopic reasonably applies. The classification SHALL be performed in the same LLM call as scoring and summarization — no additional round-trip.

#### Scenario: Article matches a subtopic
- **WHEN** a podcast has `subtopics: {"LLM releases": 10, "Dev tools": 5}` and an article about a new Anthropic model is scored
- **THEN** the LLM returns `subtopic: "LLM releases"` and the article row is persisted with `subtopic = "LLM releases"`

#### Scenario: Article does not match any subtopic
- **WHEN** the same podcast scores an article about AI ethics that does not match any configured subtopic
- **THEN** the LLM returns `subtopic: null` and the article row is persisted with `subtopic = null`

#### Scenario: LLM returns an unknown subtopic name
- **WHEN** the LLM returns `subtopic: "Frontier research"` but the podcast's subtopics map does not contain that name
- **THEN** the value is normalized to `null` before persistence

#### Scenario: Subtopics feature disabled
- **WHEN** a podcast has empty or null `subtopics`
- **THEN** the prompt does not mention subtopics, the response schema is `{relevanceScore, summary}`, and the article's `subtopic` column is left null

#### Scenario: Subtopic classification adds no extra LLM calls
- **WHEN** Stage 1 runs for a podcast with subtopics configured
- **THEN** each article still makes exactly one LLM call, and token usage is recorded once per article

### Requirement: Subtopic-aware briefing composition
When a podcast has a non-empty `subtopics` map, the briefing composer (and its dialogue/interview variants) SHALL receive the subtopics map and the persisted `subtopic` value for each article. The composer prompt SHALL:
- Group articles by their `subtopic` value, with `null` mapped to a synthetic "Other" bucket of effective weight 1.
- Partition subtopics into a full-segment tier (`weight > podcast.rapidFireWeightThreshold`) and a rapid-fire tier (`weight <= podcast.rapidFireWeightThreshold`).
- Allocate per-subtopic word budgets in the full-segment tier proportionally to weight, using a total full-segment budget of `targetWords * (1 - rapidFireBudgetFraction)`.
- Instruct the LLM to emit a labeled rapid-fire segment after the full segments, covering each rapid-fire article in one to two sentences, with a total budget of `targetWords * rapidFireBudgetFraction`.
- Use style-appropriate phrasing for the rapid-fire segment ("And in brief:" for briefing, "Quick hits before we wrap" for dialogue, "Lightning round to close" for interview).

The `rapidFireBudgetFraction` SHALL default to 0.15 and SHALL be configurable via `app.compose.rapid-fire-budget-fraction`. When a podcast has no subtopics configured, the composer SHALL use the existing flat layout with no per-subtopic budgets and no rapid-fire segment.

If after grouping articles by subtopic the full-segment tier holds zero articles — because every classified subtopic (including the synthetic "Other" bucket) has weight at or below `rapidFireWeightThreshold`, or because every article was classified `null` and "Other" is rapid-fire by default — the composer SHALL fall back to the legacy flat layout with no rapid-fire segment. The rapid-fire segment SHALL only be emitted when at least one full segment exists for it to contrast against.

#### Scenario: Briefing script contains a labeled rapid-fire segment
- **WHEN** a podcast has subtopics with weights both above and at-or-below the threshold and articles classified into both tiers
- **THEN** the generated briefing contains a clearly demarcated rapid-fire segment after the main segments (text marker "And in brief" or style-appropriate equivalent)

#### Scenario: No rapid-fire segment when all subtopics are full-tier
- **WHEN** every classified subtopic has weight strictly greater than `rapidFireWeightThreshold`
- **THEN** the composer prompt omits the rapid-fire instructions and the generated script has no rapid-fire segment

#### Scenario: No full-segment tier falls back to flat layout
- **WHEN** every classified subtopic has weight at or below the threshold (so the full-segment tier holds no articles)
- **THEN** the composer prompt falls back to the legacy flat layout with no per-subtopic segments and no rapid-fire instructions

#### Scenario: Every article classified as null falls back to flat layout
- **WHEN** the LLM returned `subtopic = null` for every article (so only the synthetic "Other" rapid-fire bucket has content)
- **THEN** the composer prompt is the legacy flat layout — no rapid-fire segment, no subtopic-shaped instructions

#### Scenario: Word budgets proportional to weight
- **WHEN** the full-tier contains `{"A": 10, "B": 5}` with `targetWords: 1500` and default `rapidFireBudgetFraction: 0.15`
- **THEN** the composer prompt asks for approximately 850 words on "A", 425 words on "B", and ≤ 225 words on the rapid-fire segment

#### Scenario: Dialogue/interview styles use style-appropriate rapid-fire phrasing
- **WHEN** a dialogue or interview podcast has rapid-fire articles
- **THEN** the rapid-fire segment uses conversational phrasing rather than a flat "And in brief:" header (e.g. one host says "Quick hits before we wrap")

#### Scenario: Backwards-compatible composition without subtopics
- **WHEN** a podcast has no subtopics configured
- **THEN** the composer prompt is byte-identical to the pre-feature behavior, with no segmentation and no rapid-fire instructions
