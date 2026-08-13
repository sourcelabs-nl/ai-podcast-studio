## Purpose

Defines the requirements for the frontend publish wizard, publications tab, and episode publishing UI on the Next.js dashboard.
## Requirements
### Requirement: Publish wizard dialog
The system SHALL provide a multi-step wizard dialog for publishing an episode to an external platform. The wizard SHALL have three steps: target selection, confirmation, and result. The confirmation step SHALL display the podcast name, episode number, generated date, selected target, and the episode summary (recap) if available.

#### Scenario: Open publish wizard
- **WHEN** user clicks the "Publish" button on an episode row in the episodes table
- **THEN** a dialog opens showing step 1 (target selection) with the episode number displayed

#### Scenario: Select target and confirm
- **WHEN** user selects a target (e.g., "SoundCloud") and clicks "Next"
- **THEN** the wizard advances to step 2 showing a confirmation view with the podcast name, episode number, generated date, selected target, and episode summary (if available)

#### Scenario: Publish success
- **WHEN** user clicks "Publish" on the confirmation step and the API call succeeds
- **THEN** the wizard advances to step 3 showing a success message with the external URL as a clickable link, and a "Done" button to close the dialog

#### Scenario: Publish failure
- **WHEN** user clicks "Publish" on the confirmation step and the API call fails
- **THEN** the wizard advances to step 3 showing an error message with the failure reason, and a "Done" button to close the dialog

#### Scenario: Already published conflict
- **WHEN** the API returns HTTP 409 (already published to this target)
- **THEN** the wizard shows an error message indicating the episode is already published to this target

#### Scenario: Close wizard refreshes data
- **WHEN** user closes the wizard dialog (via "Done" button or clicking outside)
- **THEN** the episodes list and publications data SHALL be refreshed

### Requirement: Publish button on episodes table
The system SHALL display a "Publish" button on each episode row that has status `GENERATED` and has NOT already been published. The system SHALL fetch publication status for all episodes on page load to determine which episodes are already published. Action buttons SHALL have consistent fixed width (`w-24`).

#### Scenario: Publish button visible for unpublished GENERATED episodes
- **WHEN** an episode has status `GENERATED` and no publication with status `PUBLISHED` exists
- **THEN** a "Publish" button is displayed in the actions column

#### Scenario: Publish button hidden for already published episodes
- **WHEN** an episode has status `GENERATED` but already has a publication with status `PUBLISHED`
- **THEN** the "Publish" button SHALL NOT be displayed

#### Scenario: Publish button hidden for non-GENERATED episodes
- **WHEN** an episode has status `PENDING_REVIEW`, `APPROVED`, `FAILED`, or `DISCARDED`
- **THEN** the "Publish" button SHALL NOT be displayed

### Requirement: Published badge on episodes table
The system SHALL display a "Published" badge (default variant, orange) next to the status badge for episodes that have been published to any target.

#### Scenario: Published badge displayed
- **WHEN** an episode has a publication with status `PUBLISHED`
- **THEN** a default (orange) badge with text "Published" is displayed next to the status badge

#### Scenario: Published badge not displayed
- **WHEN** an episode has no publications or only non-PUBLISHED publications
- **THEN** no "Published" badge is displayed

### Requirement: Publications tab
The system SHALL display a "Publications" tab on the podcast detail page alongside the existing "Episodes" tab. The tab SHALL show a table of all publications for the podcast's episodes with columns: #, Date, Day, Published, Status, Target, URL, Actions. The Date column SHALL display the episode's generated date. The Day column SHALL display the day of the week. The target column SHALL display a cloud icon and the properly-cased target name (e.g., "SoundCloud"). The URL column SHALL display "Track" and "Playlist" links for SoundCloud publications, where "Track" links to the individual track and "Playlist" links to the user's sets page. The Actions column SHALL display a Republish button and an Unpublish button for PUBLISHED records.

#### Scenario: Display publications table with episode date
- **WHEN** user clicks the "Publications" tab
- **THEN** a table is displayed with columns: # (episode number), Date (episode date), Day (day of week), Published (publication date), Status (badge), Target (icon + name), URL (links), Actions (Republish + Unpublish buttons)

#### Scenario: Unpublish button visible for PUBLISHED records
- **WHEN** a publication has status `PUBLISHED`
- **THEN** an Unpublish button (destructive variant, icon-only) is displayed in the actions column

#### Scenario: Unpublish button hidden for non-PUBLISHED records
- **WHEN** a publication has status `UNPUBLISHED`, `FAILED`, or `PENDING`
- **THEN** the Unpublish button is NOT displayed

#### Scenario: Unpublish confirmation dialog
- **WHEN** user clicks the Unpublish button on a publication row
- **THEN** a dialog appears asking "Are you sure you want to unpublish episode #N from {target}?" with Cancel and Unpublish buttons

#### Scenario: Unpublish confirmed
- **WHEN** user clicks "Unpublish" in the confirmation dialog
- **THEN** the system calls `DELETE .../episodes/{id}/publications/{target}` and refreshes the publications and episodes data on completion

#### Scenario: UNPUBLISHED status badge
- **WHEN** a publication has status `UNPUBLISHED`
- **THEN** the status badge uses the `secondary` variant (grey)

#### Scenario: SoundCloud URL links
- **WHEN** a SoundCloud publication has an external URL
- **THEN** the URL column displays "Track" linking to the track URL and "Playlist" linking to the user's SoundCloud sets page, separated by a pipe character

#### Scenario: Target display name
- **WHEN** a publication has target "soundcloud"
- **THEN** the target column displays a cloud icon followed by "SoundCloud"

#### Scenario: No publications
- **WHEN** the podcast has no published episodes
- **THEN** the publications tab shows an empty state message "No publications found."

#### Scenario: Publication status badges
- **WHEN** publications are displayed
- **THEN** status badges use the `default` variant (orange) for PENDING, PUBLISHED, and FAILED statuses, and `secondary` variant (grey) for UNPUBLISHED status

### Requirement: Republish from publications tab
The system SHALL display a "Republish" button on each publication row in the publications tab. Clicking the button SHALL show a confirmation dialog before calling the publish API.

#### Scenario: Republish confirmation dialog
- **WHEN** user clicks the "Republish" button on a publication row
- **THEN** a dialog appears asking "Are you sure you want to republish episode #N to {target}?" with Cancel and Republish buttons

#### Scenario: Republish confirmed
- **WHEN** user clicks "Republish" in the confirmation dialog
- **THEN** the system calls `POST .../episodes/{id}/publish/{target}` and refreshes the publications and episodes data on completion

#### Scenario: Republish cancelled
- **WHEN** user clicks "Cancel" in the confirmation dialog
- **THEN** the dialog closes and no API call is made

### Requirement: Unpublish from publications tab
The system SHALL display an "Unpublish" button (destructive variant, icon-only with Trash2 icon) on each publication row with status `PUBLISHED`. Clicking the button SHALL show a confirmation dialog before calling the unpublish API.

#### Scenario: Unpublish button visible for PUBLISHED records
- **WHEN** a publication has status `PUBLISHED`
- **THEN** an Unpublish button is displayed in the actions column

#### Scenario: Unpublish button hidden for non-PUBLISHED records
- **WHEN** a publication has status `UNPUBLISHED`, `FAILED`, or `PENDING`
- **THEN** the Unpublish button is NOT displayed

#### Scenario: Unpublish confirmation dialog
- **WHEN** user clicks the Unpublish button on a publication row
- **THEN** a dialog appears asking "Are you sure you want to unpublish episode #N from {target}?" with Cancel and Unpublish buttons

#### Scenario: Unpublish confirmed
- **WHEN** user clicks "Unpublish" in the confirmation dialog
- **THEN** the system calls `DELETE .../episodes/{id}/publications/{target}` and refreshes the publications and episodes data on completion

### Requirement: Publish wizard shows target publication status
The publish wizard target selection step SHALL fetch existing publications for the episode and display a status badge next to each target indicating its current publication state.

#### Scenario: Target already published
- **WHEN** the episode has a PUBLISHED publication for a target
- **THEN** a "Published" badge (outline variant) is displayed next to the target name

#### Scenario: Target previously failed
- **WHEN** the episode has a FAILED publication for a target
- **THEN** a "Failed" badge (destructive variant) is displayed next to the target name

#### Scenario: Target previously unpublished
- **WHEN** the episode has an UNPUBLISHED publication for a target
- **THEN** an "Unpublished" badge (secondary variant) is displayed next to the target name

#### Scenario: Target never published
- **WHEN** the episode has no publication record for a target
- **THEN** no badge is displayed next to the target name

### Requirement: EpisodePublication TypeScript type
The system SHALL define an `EpisodePublication` interface in `frontend/src/lib/types.ts` with fields: `id` (number), `episodeId` (number), `target` (string), `status` (string), `externalId` (string | null), `externalUrl` (string | null), `errorMessage` (string | null), `publishedAt` (string | null), `createdAt` (string).

#### Scenario: Type used for API responses
- **WHEN** the frontend fetches publication data from `GET .../publications`
- **THEN** the response SHALL be typed as `EpisodePublication[]`

### Requirement: SoundCloud upload quota recovery
When publishing to SoundCloud fails because the upload quota is exceeded (HTTP 413), the publish wizard SHALL offer to remove the oldest track to free space, and — after a single user confirmation — SHALL delete that track and automatically retry publishing. Because SoundCloud does not free the quota immediately after a deletion, the wizard SHALL retry on a spaced schedule (waiting before each attempt: 8s, 15s, 30s) rather than retrying immediately, and SHALL show a "republishing" progress indicator with the current attempt number while waiting. If the publish still exceeds quota after all retry attempts, the wizard SHALL surface the quota state again (with the current oldest track) so the user can wait or remove another track. The destructive delete SHALL require the one-time confirmation; the subsequent retries SHALL NOT require further user action.

#### Scenario: Quota exceeded offers removal
- **WHEN** publishing returns HTTP 413 with an oldest track
- **THEN** the wizard shows an error with the oldest track and a "Remove & republish" confirmation button

#### Scenario: Confirm triggers delete then auto-retry
- **WHEN** the user confirms removal
- **THEN** the wizard deletes the oldest track, waits before retrying, and automatically re-attempts the publish without further user clicks, showing a "republishing… (attempt N of 3)" indicator

#### Scenario: Auto-retry succeeds after space is freed
- **WHEN** a retry attempt succeeds after the deletion has propagated
- **THEN** the wizard advances to the success result with the external URL

#### Scenario: Still over quota after retries
- **WHEN** every retry attempt still returns HTTP 413
- **THEN** the wizard surfaces the quota state again so the user can wait or remove another track

#### Scenario: Done disabled during retry
- **WHEN** an auto-retry sequence is in progress
- **THEN** the "Done" button is disabled until the sequence completes

