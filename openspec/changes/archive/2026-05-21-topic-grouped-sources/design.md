## Context

The episode sources HTML page currently renders a flat `<ul>` of all source articles. The `TopicDedupFilter` already assigns topic labels to articles and stores them in `episode_articles.topic`, but the sources page ignores this data. The RSS feed generator (`FeedGenerator`) already groups articles by topic in feed descriptions, proving the data is available and useful.

The composer (briefing/dialogue/interview) controls article ordering in the script but does not expose which topics appear in which order. To order topics on the sources page by discussion order, the composer needs to output this metadata.

## Goals / Non-Goals

**Goals:**
- Sources HTML page groups articles under topic headings, ordered by how the episode discusses them
- Recap (summary) text naturally references the topics covered
- Backward-compatible: historical episodes without topic data render a flat list

**Non-Goals:**
- Shortening or rewriting topic labels (deferred to later iteration)
- Changing the RSS feed description format (already groups by topic)
- Changing the show notes text format stored in the database
- Adding topic grouping to the frontend episode detail page

## Decisions

### 1. Composer outputs topic ordering via structured response

**Decision:** Add a `topicOrder` field to `CompositionResult` containing an ordered list of topic labels as they appear in the composed script. The LLM prompt instructs the composer to output a JSON block at the end of the script with the topic ordering, which is then parsed and stripped from the script text.

**Alternatives considered:**
- *Parse topic order from script text at HTML generation time* (Option A from exploration): Fragile heuristic matching, unreliable for topics whose labels don't appear verbatim in the script.
- *Separate LLM call after composition*: Adds latency and cost for something the composer already knows.

**Approach:** Append to the composer prompt:

```
After the script, output a JSON block on a new line in this exact format:
|||TOPIC_ORDER|||
["topic label 1", "topic label 2", ...]
|||END_TOPIC_ORDER|||
```

The composer's post-processing (`stripSectionHeaders` or a new method) extracts this block, parses the JSON array, and removes it from the script text. This avoids changing the structured output format (which currently returns plain text) and works across all three composer styles.

### 2. Store topic order as integer on episode_articles

**Decision:** Add a nullable `topic_order` INTEGER column to `episode_articles`. All articles sharing the same topic get the same `topic_order` value (0-based index from the composer's ordered list). This allows grouping and ordering with a simple `ORDER BY topic_order, relevance_score DESC` query.

**Alternatives considered:**
- *Separate `episode_topic_order` table*: More normalized but adds complexity for a simple ordering concern.
- *Store ordered topic list as JSON on the episode*: Would work but duplicates info already derivable from `episode_articles`.

### 3. Sources HTML generation uses topic-grouped query

**Decision:** Replace `findRawArticlesByEpisodeId()` (returns `List<Article>`) with a new query that returns articles with their topic and topic_order. `EpisodeSourcesGenerator.generate()` accepts a grouped/ordered structure and renders topic sections with `<h3>` headings.

**Fallback:** When all articles have null `topic_order`, render a flat list under a generic heading (backward compatibility for historical episodes).

### 4. Recap prompt includes topic labels

**Decision:** Pass the list of topic labels to `EpisodeRecapGenerator.buildPrompt()` so the LLM can naturally weave topic references into the summary paragraph. The prompt addition is lightweight: just a "Topics discussed:" list appended to the existing prompt. This does not force a rigid structure; the LLM still generates a natural paragraph.

### 5. Page heading renamed

**Decision:** Rename "Sources" to "Topics Covered" when topics are available. Fall back to "Sources" for episodes without topic data.

## Risks / Trade-offs

- **LLM may not reliably output the topic order block**: The delimiter-based approach (`|||TOPIC_ORDER|||`) is robust, but if the LLM omits it, we fall back to storing null `topic_order` and rendering the flat list. No data loss occurs.
- **Topic labels may not match exactly**: The composer receives articles with topic labels from the dedup filter, but the LLM composes freely. The topic order output uses the same labels provided as input, so they should match. If a label doesn't match any stored topic, it's ignored during order assignment.
- **Recomposed episodes have no topics**: The recompose path bypasses the dedup filter, so `articleTopics` is empty. These episodes will render flat lists, which is acceptable.
