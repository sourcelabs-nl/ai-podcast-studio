# Capability: Frontend Source Management

## Purpose

Defines the requirements for managing podcast content sources (RSS, website, Twitter, YouTube) through the Next.js frontend dashboard, including listing, adding, editing, and deleting sources.

## Requirements

### Requirement: Source TypeScript interface
The frontend SHALL define a `Source` interface in `types.ts` with fields: `id` (string), `podcastId` (string), `type` (string), `url` (string), `pollIntervalMinutes` (number), `enabled` (boolean), `label` (string | null), `createdAt` (string).

#### Scenario: Source type available for use
- **WHEN** the frontend fetches source data from the API
- **THEN** the response can be typed with the `Source` interface

### Requirement: Sources tab on podcast detail page
The podcast detail page SHALL display a "Sources" tab alongside the existing "Episodes" and "Publications" tabs. The tab SHALL render a `SourcesTab` component.

#### Scenario: Sources tab visible
- **WHEN** the podcast detail page loads
- **THEN** three tabs are displayed: "Episodes" (default), "Publications", and "Sources"

#### Scenario: Sources tab shows source list
- **WHEN** user clicks the "Sources" tab
- **THEN** the `SourcesTab` component loads and displays sources for the podcast

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

### Requirement: Add source dialog
The Sources tab SHALL display an "Add Source" button that opens a dialog. The dialog SHALL contain a form with fields: type (select with options: rss, website, twitter, youtube), url (text input), label (text input, optional), poll interval in minutes (number input, default 30), and enabled (switch, default true). Submitting the form SHALL call `POST /api/users/{userId}/podcasts/{podcastId}/sources` and refresh the source list.

#### Scenario: Add a new source
- **WHEN** user clicks "Add Source", fills in the form, and submits
- **THEN** the source is created via the API, the dialog closes, and the source list refreshes

#### Scenario: Cancel add
- **WHEN** user opens the Add Source dialog and clicks cancel
- **THEN** the dialog closes without creating a source

### Requirement: Edit source dialog
Each source row SHALL have an edit button that opens a dialog pre-populated with the source's current values. Submitting the form SHALL call `PUT /api/users/{userId}/podcasts/{podcastId}/sources/{sourceId}` and refresh the source list.

#### Scenario: Edit an existing source
- **WHEN** user clicks edit on a source row, modifies fields, and submits
- **THEN** the source is updated via the API, the dialog closes, and the source list refreshes

#### Scenario: Cancel edit
- **WHEN** user opens the Edit dialog and clicks cancel
- **THEN** the dialog closes without updating the source

### Requirement: Delete source with confirmation
Each source row SHALL have a delete button. Clicking it SHALL open an AlertDialog warning that deleting the source will also delete all associated articles. Confirming SHALL call `DELETE /api/users/{userId}/podcasts/{podcastId}/sources/{sourceId}` and refresh the source list.

#### Scenario: Delete a source
- **WHEN** user clicks delete, confirms in the AlertDialog
- **THEN** the source is deleted via the API and the source list refreshes

#### Scenario: Cancel delete
- **WHEN** user clicks delete and then cancels the AlertDialog
- **THEN** the source is not deleted

### Requirement: Export sources as markdown
The Sources tab SHALL display a "Download" button next to the "Add source" button. Clicking it SHALL generate a markdown file from the loaded sources and trigger a browser download. The file SHALL be named `sources.md`. Sources SHALL be grouped by type (RSS, Website, Twitter, YouTube) with each entry showing the label (or URL if no label), URL, and poll interval. Disabled sources SHALL be included with a "(disabled)" marker.

#### Scenario: Download sources markdown
- **WHEN** user clicks the "Download" button on the Sources tab
- **THEN** a `sources.md` file is downloaded containing all sources grouped by type

#### Scenario: Source entry with label
- **WHEN** a source has label "Hacker News" and URL "https://hnrss.org/frontpage" with 30m interval
- **THEN** the markdown entry reads `- [Hacker News](https://hnrss.org/frontpage) - 30m`

#### Scenario: Source entry without label
- **WHEN** a source has no label and URL "https://example.com/feed"
- **THEN** the markdown entry reads `- https://example.com/feed - 30m`

#### Scenario: Disabled source
- **WHEN** a source is disabled
- **THEN** the entry includes a "(disabled)" marker

#### Scenario: No sources
- **WHEN** the podcast has no sources
- **THEN** the download button is still visible but the generated file contains only the heading with no entries

#### Scenario: Download button placement
- **WHEN** the Sources tab renders
- **THEN** the download button appears next to the "Add source" button using the `Download` lucide icon
