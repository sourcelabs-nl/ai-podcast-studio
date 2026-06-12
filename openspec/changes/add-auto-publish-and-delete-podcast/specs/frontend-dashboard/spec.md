## ADDED Requirements

### Requirement: Delete podcast with typed-name confirmation
The podcast detail page SHALL provide a destructive "Delete podcast" action in a clearly separated danger zone. Activating it SHALL open a confirmation dialog that explains the deletion is permanent and cascades to all episodes, sources, and audio. The dialog SHALL require the user to type the exact podcast name; the confirm button SHALL remain disabled until the typed text matches the podcast name exactly. On confirmation the frontend SHALL call `DELETE /users/{userId}/podcasts/{podcastId}` and, on success, navigate to the podcast list.

#### Scenario: Confirm button disabled until name matches
- **WHEN** the user opens the delete dialog and the typed text does not exactly match the podcast name
- **THEN** the Delete confirm button is disabled

#### Scenario: Successful deletion redirects to list
- **WHEN** the user types the exact podcast name and clicks Delete, and the backend returns HTTP 204
- **THEN** the podcast is deleted and the user is navigated to the podcast list

#### Scenario: Cancel leaves podcast intact
- **WHEN** the user opens the delete dialog and clicks Cancel
- **THEN** the dialog closes and no delete request is sent
