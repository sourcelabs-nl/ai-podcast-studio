## MODIFIED Requirements

### Requirement: Show notes generation
The system SHALL generate show notes for each episode after the episode-article links are saved. Show notes SHALL consist of the episode recap followed by a "Sources:" section listing each linked article's title and URL. Articles SHALL be sorted by relevance score descending. Article titles longer than 100 characters SHALL be truncated with "...".

The recap generation prompt SHALL include the list of topic labels (from the dedup filter) so the LLM can naturally reference the topics discussed in the episode. The recap remains a natural prose paragraph; topics are provided as context, not as a rigid structure to follow.

#### Scenario: Recap references topics naturally
- **WHEN** an episode is created from articles spanning topics ["AI Agent Safety", "New Model Releases", "Code Quality Benchmarks"] and a recap is generated
- **THEN** the recap text naturally mentions at least some of the topic areas without being a bullet list of topics

#### Scenario: Recap generated without topic data
- **WHEN** an episode is recomposed (no topic data available) and a recap is generated
- **THEN** the recap is generated from the script text alone, without topic context, matching current behavior
