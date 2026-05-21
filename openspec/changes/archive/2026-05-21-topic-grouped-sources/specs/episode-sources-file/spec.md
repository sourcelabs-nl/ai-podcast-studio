## MODIFIED Requirements

### Requirement: Episode sources.md generation
The system SHALL generate a standalone HTML file for each episode during the episode creation pipeline. The file SHALL be stored at `data/episodes/{podcastId}/{slug}-sources.html` where `slug` is derived from the audio file path or generation timestamp.

The file SHALL contain:
1. A heading with the podcast name
2. The episode date
3. The episode recap (summary)
4. A "Topics Covered" section with articles grouped by topic

When topic data is available (articles have non-null `topic_order`), articles SHALL be grouped under `<h3>` topic headings, ordered by the `topic_order` value (reflecting the order topics are discussed in the episode). Within each topic group, articles SHALL be ordered by relevance score descending. The section heading SHALL be "Topics Covered".

When topic data is NOT available (all articles have null `topic_order`, e.g., historical episodes or recomposed episodes), articles SHALL be rendered as a flat unordered list under the heading "Sources", preserving backward compatibility.

#### Scenario: Sources file with topic-grouped articles
- **WHEN** an episode is created with a recap and 10 linked articles across 3 topics, each with a `topic_order` value
- **THEN** the HTML file contains the recap, followed by a "Topics Covered" heading, followed by 3 topic sections each with an `<h3>` topic label and a `<ul>` of articles belonging to that topic, ordered by `topic_order` ascending

#### Scenario: Sources file without topic data (historical episode)
- **WHEN** an episode has linked articles but all have null `topic_order`
- **THEN** the HTML file renders all articles as a flat `<ul>` under a "Sources" heading

#### Scenario: Sources file with mixed topic data
- **WHEN** an episode has some articles with topics and some without
- **THEN** articles with topics are grouped under their topic headings first, followed by ungrouped articles under a separate section at the end

#### Scenario: Sources file generated without recap
- **WHEN** an episode is created but recap generation fails
- **THEN** the HTML file is generated without the recap section, containing only the podcast name, date, and topic-grouped sources

#### Scenario: Sources file generated without articles
- **WHEN** an episode is created with a recap but no linked articles
- **THEN** the HTML file is generated with the podcast name, date, and recap, but no sources/topics section
