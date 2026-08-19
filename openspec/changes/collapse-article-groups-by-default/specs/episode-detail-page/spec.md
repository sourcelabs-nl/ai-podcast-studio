## MODIFIED Requirements

### Requirement: Articles tab with grouped display
The Articles tab SHALL fetch articles from the episode articles API endpoint and display them grouped by source. Each source group SHALL be collapsible. Articles within each group SHALL be sorted by relevance score descending.

Every source group SHALL start collapsed when the tab is opened, so the tab presents a scannable list of source names with their article counts rather than every article at once. An episode links dozens of articles across many sources, which expanded by default buries the overview the grouping exists to provide. The collapsed and expanded states SHALL be distinguished by the group's chevron direction.

#### Scenario: Articles grouped by source
- **WHEN** the Articles tab is activated
- **THEN** articles are fetched from `GET /api/users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/articles` and displayed in collapsible groups by source

#### Scenario: Groups start collapsed
- **WHEN** the Articles tab is opened
- **THEN** every source group is collapsed, showing its name and article count with no article cards, and each group's chevron points right

#### Scenario: Expanding a group reveals its articles
- **WHEN** the user clicks a collapsed source group
- **THEN** that group's article cards are revealed, its chevron points down, and the other groups stay collapsed

#### Scenario: Collapsing a group hides its articles again
- **WHEN** the user clicks an expanded source group
- **THEN** its article cards are hidden and its chevron points right again

#### Scenario: Article count in tab label
- **WHEN** articles have been loaded
- **THEN** the Articles tab label displays the count, e.g., "Articles (37)"

#### Scenario: Empty articles
- **WHEN** the episode has no linked articles
- **THEN** the Articles tab displays a message indicating no articles are linked
