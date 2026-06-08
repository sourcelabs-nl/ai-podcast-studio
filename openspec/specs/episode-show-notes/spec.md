# Capability: Episode Show Notes

## Purpose

Generates and stores show notes for each episode, providing a recap summary for use in RSS feed descriptions and API responses.
## Requirements
### Requirement: Show notes generation
The system SHALL generate show notes for each episode after the episode-article links are saved. Show notes SHALL consist of the episode recap followed by a "Sources:" section listing each linked article's title and URL. Articles SHALL be sorted by relevance score descending. Article titles longer than 100 characters SHALL be truncated with "...".

The recap generation prompt SHALL include the list of topic labels (from the dedup filter) so the LLM can naturally reference the topics discussed in the episode. The recap remains a natural prose paragraph; topics are provided as context, not as a rigid structure to follow.

#### Scenario: Recap references topics naturally
- **WHEN** an episode is created from articles spanning topics ["AI Agent Safety", "New Model Releases", "Code Quality Benchmarks"] and a recap is generated
- **THEN** the recap text naturally mentions at least some of the topic areas without being a bullet list of topics

#### Scenario: Recap generated without topic data
- **WHEN** an episode is recomposed (no topic data available) and a recap is generated
- **THEN** the recap is generated from the script text alone, without topic context, matching current behavior

### Requirement: Show notes storage
The Episode entity SHALL have a `show_notes` TEXT column to store the generated show notes.

#### Scenario: Database migration
- **WHEN** the application starts after the migration
- **THEN** the episodes table has a `show_notes` column that is nullable

### Requirement: Show notes in API response
The episode API response SHALL include a `showNotes` field.

#### Scenario: Episode response includes show notes
- **WHEN** a GET request is made for an episode that has show notes
- **THEN** the response JSON includes a `showNotes` field with the show notes text

### Requirement: Recap identifies the topics discussed in the script
When generating an episode recap, the system SHALL also identify which of the candidate topic labels are actually discussed in the episode script, using the same LLM call that produces the recap (no additional request). The result SHALL be the subset of the provided candidate labels, matched by their exact label text.

After the recap is stored, the system SHALL set `topic_order` to `NULL` on every linked article whose topic is not in the discussed subset, while retaining the article link and its topic label. If the discussed subset is empty (e.g. the model returned nothing or the response could not be parsed), the system SHALL make no changes to `topic_order`.

When regenerating the recap for an existing episode, the system SHALL derive the candidate topic labels from the episode's existing linked articles (distinct topics with a non-null `topic_order`, in topic order) and pass them to recap generation, so that re-running recap regeneration corrects the discussed set for already-generated episodes.

#### Scenario: Recap prunes non-discussed topics
- **WHEN** recap generation runs for an episode whose links span 42 candidate topics and the model reports 15 discussed topics
- **THEN** the 27 non-discussed topics' articles have `topic_order` set to `NULL`, and the 15 discussed topics retain their `topic_order`

#### Scenario: Empty discussed subset is a no-op
- **WHEN** recap generation cannot determine any discussed topics
- **THEN** no `topic_order` values are changed

#### Scenario: Regeneration corrects an existing episode
- **WHEN** `regenerate-recap` is invoked for an episode that currently lists every linked topic
- **THEN** candidate labels are taken from the existing links, the discussed subset is recomputed, and non-discussed topics are pruned to background

