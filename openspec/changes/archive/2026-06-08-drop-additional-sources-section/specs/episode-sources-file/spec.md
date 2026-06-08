## MODIFIED Requirements

### Requirement: Episode sources.md generation
The system SHALL generate a standalone HTML file for each episode during the episode creation pipeline, stored at `data/episodes/{podcastId}/{slug}-sources.html`.

The file SHALL contain:
1. A heading with the podcast name
2. The episode date
3. A metadata line (audio length, number of sources, listen link) where data is available
4. A Summary section (showNotes, falling back to recap, falling back to a sanitized script snippet)
5. A "Topics Covered" section listing, grouped by topic, only the articles whose topic was actually discussed in the episode (non-null `topic_order`). Articles not discussed SHALL NOT be listed. When the episode has no topic data at all, a flat "Sources" list of all linked articles is rendered instead.

The page SHALL NOT render an "Additional Sources" section.

Article titles longer than 120 characters SHALL be truncated to 120 characters with "..." appended when rendered as link text.

#### Scenario: Non-discussed articles are omitted
- **WHEN** an episode links articles whose topic was discussed (non-null `topic_order`) and others whose topic was not (null `topic_order`)
- **THEN** the page lists only the discussed topics under "Topics Covered" and renders no "Additional Sources" section

#### Scenario: Long article title truncated for display
- **WHEN** an article has a title of 200 characters
- **THEN** the link text shows the first 120 characters followed by "..."

#### Scenario: Legacy episode with no topic data
- **WHEN** an episode's linked articles all have null `topic_order`
- **THEN** the page renders a flat "Sources" list of those articles
