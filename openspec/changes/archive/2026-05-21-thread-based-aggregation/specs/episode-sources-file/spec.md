## MODIFIED Requirements

### Requirement: Episode sources.md generation
The system SHALL generate a standalone HTML file for each episode during the episode creation pipeline. The file SHALL be stored at `data/episodes/{podcastId}/{slug}-sources.html` where `slug` is derived from the audio file path or generation timestamp.

The file SHALL contain:
1. A heading with the podcast name
2. The episode date
3. The episode recap (summary)
4. A "Topics Covered" section with articles grouped by topic (when topic data is available), or a flat "Sources" list (when no topic data)

Article titles longer than 120 characters SHALL be truncated to 120 characters with "..." appended when rendered as link text in the HTML.

#### Scenario: Long article title truncated for display
- **WHEN** an article has a title of 200 characters
- **THEN** the link text in the HTML shows the first 120 characters followed by "..."

#### Scenario: Short article title not truncated
- **WHEN** an article has a title of 80 characters
- **THEN** the link text in the HTML shows the full title

#### Scenario: Title truncation does not affect URL or stored data
- **WHEN** an article title is truncated for display
- **THEN** the `<a href="...">` still links to the correct URL and the article title in the database is unchanged
