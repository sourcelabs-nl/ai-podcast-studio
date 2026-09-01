## MODIFIED Requirements

### Requirement: Source list table
The `SourcesTab` component SHALL fetch sources from `GET /api/users/{userId}/podcasts/{podcastId}/sources` and display them in a table with columns: Label (display label, falling back to URL if null), Type (source type badge), Poll Interval (formatted as minutes), Enabled (visual indicator), Articles (total count with relevance percentage), Posts, and Actions (edit and delete buttons).

The `Enabled` column header SHALL carry a filter dropdown offering All, Enabled and Disabled, following the status filter already on the episodes list. The selection SHALL be passed to the backend as the `enabled` query parameter rather than applied to the fetched rows, and changing it SHALL refetch.

The filter SHALL default to **Enabled**, so the tab opens showing only sources that actually run. Retired sources are disabled rather than deleted, so the disabled set grows over time and would otherwise dominate the table.

#### Scenario: Display sources
- **WHEN** the Sources tab loads and the podcast has sources
- **THEN** the enabled sources are displayed in a table with the specified columns

#### Scenario: Disabled sources hidden by default
- **WHEN** the Sources tab loads and the podcast has 24 enabled and 37 disabled sources
- **THEN** 24 rows are shown, and the request carried `enabled=true`

#### Scenario: Showing all sources
- **WHEN** the reader selects All in the Enabled column filter
- **THEN** the sources are refetched without the `enabled` parameter and both enabled and disabled rows are shown

#### Scenario: Showing only disabled sources
- **WHEN** the reader selects Disabled in the Enabled column filter
- **THEN** the sources are refetched with `enabled=false` and only disabled rows are shown

#### Scenario: No sources
- **WHEN** the Sources tab loads and the podcast has no sources matching the filter
- **THEN** an empty state message is displayed

#### Scenario: Source label fallback
- **WHEN** a source has a null label
- **THEN** the URL is displayed in the Label column instead

#### Scenario: Articles column with relevance percentage
- **WHEN** a source has 42 articles and 18 are relevant
- **THEN** the Articles column displays "42 (43% relevant)"

#### Scenario: Articles column with zero articles
- **WHEN** a source has 0 articles
- **THEN** the Articles column displays "0"

#### Scenario: Articles column with zero relevant
- **WHEN** a source has articles but none are relevant
- **THEN** the Articles column displays "42 (0% relevant)"
