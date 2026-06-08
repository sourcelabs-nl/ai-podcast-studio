## MODIFIED Requirements

### Requirement: Subtopic-aware pacing in composer
When a podcast has a non-empty `subtopics` map, the composer SHALL group the relevant articles by their persisted `subtopic` value (with `null` mapped to a synthetic "Other" bucket of effective weight 1) and SHALL allocate script time per subtopic according to weight. Subtopics with weight strictly greater than `rapidFireWeightThreshold` SHALL receive a full segment whose word budget is proportional to weight: `wordsForSubtopic = fullSegmentBudget * weight / sumOfFullWeights`, where `fullSegmentBudget = targetWords * (1 - rapidFireBudgetFraction)`. Subtopics in the rapid-fire tier SHALL share a single combined segment whose word budget is `targetWords * rapidFireBudgetFraction`. The default `rapidFireBudgetFraction` SHALL be 0.15 and SHALL be configurable via `app.compose.rapid-fire-budget-fraction`.

Rapid-fire articles SHALL be capped at `effectiveMaxItems = podcast.rapidFireMaxItems ?? app.compose.rapid-fire-max-items` (default 6) and ranked as defined in the "Rapid-fire item cap and ranking per podcast" requirement. The composer prompt SHALL enumerate the kept rapid-fire articles as a numbered list of the form `N. [bucket] "title"` in priority order, so the LLM knows exactly which articles to cover and in what order. This numbered list is input ordering only: the LLM SHALL NOT read ordinal numbers aloud (no "item one, item two", no "first, second, third") and SHALL NOT count the items aloud, instead introducing the segment in plain, natural language and flowing from one item to the next conversationally.

The composer prompt SHALL instruct the LLM to produce a labeled rapid-fire segment after the full segments, with style-appropriate phrasing (e.g. "And in brief:" for briefing, "Quick hits before we wrap" for dialogue, "Lightning round to close" for interview). Inside the rapid-fire segment, each kept article SHALL receive approximately `rapidFireWordBudget / keptCount` words of coverage (rounded down), and the LLM SHALL be instructed not to merge multiple items into one sentence and not to skip any item from the list. Each rapid-fire item SHALL voice at most one number, rounded to a clean value, and SHALL NOT read a benchmark name together with its score, leading with what the item means rather than the metric.

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

#### Scenario: Rapid-fire prompt lists kept articles in priority order with per-item budget
- **WHEN** the rapid-fire tier has 2 kept articles and `rapidFireWordBudget = 225`
- **THEN** the composer prompt contains "exactly 2 items", "~112 words per item", and a numbered list `1. [bucket] "title"`, `2. [bucket] "title"` in ranking order

#### Scenario: Rapid-fire prompt forbids counting items aloud
- **WHEN** the rapid-fire segment prompt is built for any style
- **THEN** the prompt instructs the LLM not to announce ordinal numbers or count the items aloud, and to voice at most one rounded number per item

#### Scenario: Backwards compatibility
- **WHEN** a podcast has no subtopics configured
- **THEN** the composer prompt contains no mention of subtopics or rapid-fire
