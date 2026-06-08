# Capability: Episode Sources File

## Purpose

Generates a standalone `sources.md` markdown file for each episode, listing the podcast name, date, recap, and source articles.
## Requirements
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
