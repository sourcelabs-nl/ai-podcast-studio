## ADDED Requirements

### Requirement: Per-podcast subtopic configuration
Each podcast SHALL support an optional list of weighted subtopics. The list SHALL be stored as a JSON map (`subtopics TEXT` column on `podcasts`) of subtopic name → integer weight. Weights SHALL be integers in the inclusive range 1-10. Subtopic names SHALL be non-empty strings, treated case-insensitively for matching but preserving the editor's casing for display. The map MAY be empty or null — in that case the feature is disabled and the pipeline behaves as if subtopics did not exist.

#### Scenario: Podcast with subtopics configured
- **WHEN** a podcast is saved with `subtopics: {"LLM releases": 10, "Dev tools": 5, "AI ethics": 2}`
- **THEN** the JSON map is persisted on the `subtopics` column and returned by GET unchanged

#### Scenario: Podcast with no subtopics
- **WHEN** a podcast is saved with `subtopics: {}` (or null)
- **THEN** the feature is disabled, articles receive no subtopic assignment, and the composer uses the existing flat layout

#### Scenario: Weight out of range rejected
- **WHEN** a podcast is saved with `subtopics: {"X": 0}` or `subtopics: {"Y": 11}`
- **THEN** the request SHALL be rejected with HTTP 400 and an error message identifying the invalid weight

#### Scenario: Empty subtopic name rejected
- **WHEN** a podcast is saved with `subtopics: {"": 5}`
- **THEN** the request SHALL be rejected with HTTP 400

### Requirement: Rapid-fire weight threshold per podcast
Each podcast SHALL have a `rapidFireWeightThreshold` field (INTEGER, NOT NULL, default 3). Subtopics with weight at or below this threshold SHALL be treated as the rapid-fire tier by the composer. Subtopics with weight strictly greater than the threshold SHALL be treated as full-segment tier. Valid range: 0-10 (0 means everything is full segment, 10 means everything is rapid-fire).

#### Scenario: Default threshold
- **WHEN** a podcast is created without specifying `rapidFireWeightThreshold`
- **THEN** the value is set to 3

#### Scenario: Threshold separates tiers
- **WHEN** a podcast has `rapidFireWeightThreshold: 3` and subtopics `{"A": 10, "B": 5, "C": 3, "D": 1}`
- **THEN** the composer treats A and B as full-segment tier and C and D as rapid-fire tier

#### Scenario: Threshold out of range rejected
- **WHEN** a podcast is saved with `rapidFireWeightThreshold: -1` or `rapidFireWeightThreshold: 11`
- **THEN** the request SHALL be rejected with HTTP 400

### Requirement: Subtopic classification during scoring
When a podcast has a non-empty `subtopics` map, the Stage 1 LLM call (`ArticleScoreSummarizer`) SHALL include the subtopic names in the prompt and SHALL request a JSON response containing a `subtopic` field in addition to `relevanceScore` and `summary`. The LLM SHALL pick the best-matching subtopic name verbatim from the provided list, or return `null` if no listed subtopic fits. The chosen value SHALL be persisted on the article row (`articles.subtopic TEXT`, nullable). When a podcast has no subtopics configured, the prompt and response schema SHALL remain unchanged from the pre-feature behavior, and the article's `subtopic` column SHALL be left null.

#### Scenario: Article matches a configured subtopic
- **WHEN** a podcast has `subtopics: {"LLM releases": 10, "Dev tools": 5}` and an article about a new OpenAI model is scored
- **THEN** the persisted article has `subtopic = "LLM releases"`

#### Scenario: Article does not match any subtopic
- **WHEN** the same podcast scores an article about an unrelated on-topic story (e.g. AI policy news, not in the subtopic list)
- **THEN** the persisted article has `subtopic = null` and proceeds to the composer in the catch-all bucket

#### Scenario: Subtopic must match the configured list verbatim
- **WHEN** the LLM returns a `subtopic` value that does not appear in the podcast's subtopics map
- **THEN** the system SHALL normalize unknown values to `null` and persist `null` on the article

#### Scenario: Subtopics feature disabled
- **WHEN** a podcast with empty `subtopics` is scored
- **THEN** the LLM prompt does not mention subtopics, the response schema is the legacy `{relevanceScore, summary}`, and the article's `subtopic` column is left null

### Requirement: Subtopic-aware pacing in composer
When a podcast has a non-empty `subtopics` map, the composer SHALL group the relevant articles by their persisted `subtopic` value (with `null` mapped to a synthetic "Other" bucket of effective weight 1) and SHALL allocate script time per subtopic according to weight. Subtopics with weight strictly greater than `rapidFireWeightThreshold` SHALL receive a full segment whose word budget is proportional to weight: `wordsForSubtopic = fullSegmentBudget * weight / sumOfFullWeights`, where `fullSegmentBudget = targetWords * (1 - rapidFireBudgetFraction)`. Subtopics in the rapid-fire tier SHALL share a single combined segment whose word budget is `targetWords * rapidFireBudgetFraction`. The default `rapidFireBudgetFraction` SHALL be 0.15 and SHALL be configurable via `app.compose.rapid-fire-budget-fraction`.

The composer prompt SHALL instruct the LLM to produce a labeled rapid-fire segment after the full segments, with style-appropriate phrasing (e.g. "And in brief:" for briefing, "Before we wrap, a few quick hits:" for dialogue, "Lightning round to close:" for interview). Inside the rapid-fire segment, each article SHALL receive one to two sentences of coverage.

When a podcast has no subtopics configured, the composer SHALL use the existing flat layout with no rapid-fire segment.

If after grouping articles by subtopic the full-segment tier contains zero articles (because every classified subtopic — including the synthetic "Other" bucket — has weight at or below `rapidFireWeightThreshold`, or because every article fell into the catch-all bucket and "Other" is rapid-fire by default), the composer SHALL fall back to the existing flat layout with no rapid-fire segment. This rule applies uniformly: the rapid-fire segment SHALL only be emitted when there is at least one full segment for it to contrast against.

#### Scenario: Subtopic with weight above threshold gets a full segment
- **WHEN** a podcast has `subtopics: {"LLMs": 10, "Other AI": 1}` and `rapidFireWeightThreshold: 3`, and three articles classified as `"LLMs"` plus one as `"Other AI"`
- **THEN** the script contains a full segment for "LLMs" covering its three articles, followed by a rapid-fire segment containing the one "Other AI" article

#### Scenario: All subtopics in full tier produce no rapid-fire segment
- **WHEN** every subtopic in the podcast has weight greater than `rapidFireWeightThreshold` and every article maps to one of them
- **THEN** the script contains no rapid-fire segment and uses the full word budget for the full segments

#### Scenario: No full-segment tier falls back to flat layout
- **WHEN** every classified subtopic has weight at or below `rapidFireWeightThreshold` (e.g. all weights are 1-3, or all articles fell into the synthetic "Other" bucket)
- **THEN** the composer falls back to the flat layout with no per-subtopic segments and no rapid-fire label, producing a normal script over all available articles

#### Scenario: All articles in catch-all bucket falls back to flat layout
- **WHEN** every article was classified as `subtopic = null` so only the synthetic "Other" bucket (weight 1) has content and no other subtopic bucket holds any article
- **THEN** the composer falls back to the flat layout — no rapid-fire segment is emitted, the script is a single normal flow over all articles

#### Scenario: Uncategorized articles fall into rapid-fire by default
- **WHEN** the rapid-fire threshold is the default 3 and some articles have `subtopic = null`
- **THEN** those articles are grouped into a synthetic "Other" bucket with weight 1 and included in the rapid-fire segment

#### Scenario: Word budget proportional to weight
- **WHEN** the full-segment tier contains subtopics `{"A": 10, "B": 5}` with `targetWords = 1500` and `rapidFireBudgetFraction = 0.15`
- **THEN** "A" is allocated approximately 850 words (1275 * 10/15) and "B" approximately 425 words (1275 * 5/15) in the composer prompt

#### Scenario: Backwards compatibility
- **WHEN** a podcast has no subtopics configured
- **THEN** the composer prompt is byte-identical to the pre-feature prompt and contains no mention of subtopics or rapid-fire

### Requirement: Subtopic exposed on episode article responses
The episode-articles API response SHALL include the `subtopic` field per article. The field SHALL be a string (the chosen subtopic name) or null. This enables the frontend to display which subtopic each article was classified into.

#### Scenario: Article subtopic returned in API
- **WHEN** a GET request retrieves the articles for an episode whose podcast has subtopics configured
- **THEN** each article entry includes a `subtopic` field with either the chosen subtopic name or null
