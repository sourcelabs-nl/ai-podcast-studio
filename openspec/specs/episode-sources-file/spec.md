# Capability: Episode Sources File

## Purpose

Generates a standalone `sources.md` markdown file for each episode, listing the podcast name, date, recap, and source articles.
## Requirements
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

## Sources

- [No Keys, No LLM: Building a Wikidata Definition API](https://foojay.io/today/embabel-spring-boot-wikidata-definition-api/)
- [Java Performance Update: From JDK 21 to JDK 25](https://inside.java/2026/03/08/jfokus-java-performance-update/)
```

#### Scenario: Sources file generated during pipeline
- **WHEN** an episode is created with a recap and 5 linked articles
- **THEN** a `sources.md` file is generated at `data/episodes/{podcastId}/{slug}-sources.md` containing the podcast name, date, recap, and all 5 sources

#### Scenario: Sources file generated without recap
- **WHEN** an episode is created but recap generation fails
- **THEN** the `sources.md` file is generated without the recap section, containing only the podcast name, date, and sources

#### Scenario: Sources file generated without articles
- **WHEN** an episode is created with a recap but no linked articles
- **THEN** the `sources.md` file is generated with the podcast name, date, and recap, but no sources section

#### Scenario: Article titles truncated
- **WHEN** an article title exceeds 100 characters
- **THEN** the title is truncated to 100 characters with "..." appended in the sources.md

### Requirement: Generate sources.md for existing episodes
The system SHALL provide a mechanism (startup task or admin endpoint) to regenerate `sources.md` files for all existing episodes that have linked articles. This ensures existing episodes have sources files available for FTP upload.

#### Scenario: Regenerate for existing episodes
- **WHEN** the regeneration is triggered
- **THEN** all existing GENERATED episodes with linked articles get a `sources.md` file created in their podcast's data directory
