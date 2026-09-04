# Capability: Episode Regeneration

## Purpose

Re-compose an existing episode's script using its original linked articles and the podcast's current settings, creating a new episode without affecting the regular generation pipeline.

## Requirements

### Requirement: Regenerate episode script
The system SHALL provide an endpoint to regenerate an episode's script by re-running only the composition stage of the LLM pipeline with the episode's original articles and the podcast's current configuration.

The system SHALL verify that the source episode has linked articles BEFORE creating the new GENERATING episode. Regeneration recomposes from the source episode's `episode_articles` rows, so an episode that failed before article selection can never be regenerated; creating an episode first and only then discovering this leaves a junk FAILED episode behind for every attempt. Episode 191 failed in the dedup stage, and two regenerate attempts against it produced exactly that, episodes 192 and 193.

#### Scenario: Successful regeneration
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/regenerate` request is received for an existing episode with linked articles
- **THEN** the system creates a new episode with a freshly composed script, links it to the same articles, and returns HTTP 202 with the new episode id

#### Scenario: Episode has no linked articles
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/regenerate` request is received for an episode with no entries in `episode_articles`
- **THEN** the system returns HTTP 409 with an error explaining that the episode has no articles to recompose and that a fresh generation is needed, and NO new episode is created

#### Scenario: Non-existing episode or podcast
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/regenerate` request is received for an episode or podcast that does not exist, or belongs to a different user/podcast
- **THEN** the system returns HTTP 404

### Requirement: Preserve source episode
The regenerated episode SHALL be created as a new episode. The source episode SHALL remain unmodified.

#### Scenario: The source episode is left alone
- **WHEN** regeneration of episode 191 completes
- **THEN** a new episode holds the freshly composed script and episode 191 is unchanged

#### Scenario: A failed regeneration does not damage the source
- **WHEN** regeneration fails part-way through
- **THEN** the source episode still holds its original script and status

### Requirement: Inherit source episode timestamp
The regenerated episode's `generatedAt` field SHALL be set to the same value as the source episode's `generatedAt`, not the current time.

#### Scenario: The regenerated episode keeps the original date
- **WHEN** the source episode's `generatedAt` is `2026-08-31T13:00:00Z` and regeneration runs a day later
- **THEN** the new episode's `generatedAt` is `2026-08-31T13:00:00Z`, not the time of the regeneration

### Requirement: No pipeline side effects
Regeneration SHALL NOT update the podcast's `lastGeneratedAt` timestamp. This ensures that the regular generation pipeline continues to find articles based on the original generation window.

#### Scenario: The podcast's last-generated timestamp is untouched
- **WHEN** regeneration completes
- **THEN** the podcast's `lastGeneratedAt` is unchanged, so the next scheduled run still looks at the same article window

### Requirement: Composition-only pipeline
Regeneration SHALL only run the composition stage of the LLM pipeline (script generation). It SHALL NOT re-run article aggregation, scoring, or summarization. The articles' existing scores and summaries are used as-is.

Regeneration SHALL recompose with the source episode's persisted follow-up annotations, so the composer receives the same `[FOLLOW-UP: ...]` headers the original composition received. Dedup is not re-run, so without persisted annotations a regeneration has no continuity signal at all: it then falls back on the `searchPastEpisodes` tool, whose keyword recall is weaker than dedup's comparison of titles and summaries against the actual historical article set. That is how a regeneration of episode 197 opened by claiming the GPT-6 Astra launch had been "covered yesterday" when the previous episode never mentioned it, and demoted the day's lead story on that basis.

A regenerated episode SHALL persist the annotations it composed with, so that regenerating a regenerated episode stays faithful.

An episode whose links carry no annotation (one generated before the annotation was persisted) SHALL recompose with none, which is the pre-existing behaviour.

#### Scenario: Only the composition stage runs
- **WHEN** regeneration runs for an episode with linked articles
- **THEN** the script is recomposed and no article aggregation, scoring or summarization is performed

#### Scenario: Existing scores and summaries are reused
- **WHEN** the source episode's articles already carry a relevance score and summary
- **THEN** those values are used as they are, and no scoring call is made

#### Scenario: The source episode's follow-up annotations are reused
- **WHEN** the source episode has an article whose link carries a follow-up context
- **THEN** the composer receives that article under a `[FOLLOW-UP: ...]` header, exactly as the original composition did

#### Scenario: A regenerated episode re-persists its annotations
- **WHEN** a regeneration composes with follow-up annotations
- **THEN** the new episode's own article links carry the same annotations

#### Scenario: An episode with no stored annotations recomposes without them
- **WHEN** the source episode's links carry no follow-up context
- **THEN** the composer receives no `[FOLLOW-UP: ...]` header and treats every article as new

