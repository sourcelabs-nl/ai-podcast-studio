# Capability: Frontend Publication Settings

## Purpose

Frontend UI for managing publication credentials and per-podcast publication target configuration.
## Requirements
### Requirement: Publication credentials settings page
The frontend SHALL provide a "Publication Credentials" section in the user settings area. This page SHALL display a card for each supported publication target (FTP, SoundCloud) with:
- A form for the target's credential fields
- A "Test Connection" button that tests the form values before saving
- A "Save" button that persists the credentials
- Visual feedback for test results (success/failure message)

For SoundCloud, the card SHALL additionally show the OAuth connection status and quota (reusing the existing SoundCloud status endpoint).

#### Scenario: FTP credential form
- **WHEN** the user navigates to Publication Credentials
- **THEN** an FTP card is displayed with fields: Host, Port (default 21), Username, Password, and a "Use TLS" checkbox (default checked)

#### Scenario: SoundCloud credential form
- **WHEN** the user navigates to Publication Credentials
- **THEN** a SoundCloud card is displayed with fields: Client ID, Client Secret, Callback URI, plus OAuth status and quota info

#### Scenario: Test FTP connection from form
- **WHEN** the user fills in FTP fields and clicks "Test Connection"
- **THEN** the form values are sent to `POST /users/{userId}/publishing/test/ftp` and the result is displayed (success or failure message)

#### Scenario: Test SoundCloud connection
- **WHEN** the user clicks "Test" on the SoundCloud card
- **THEN** a request is sent to `POST /users/{userId}/publishing/test/soundcloud` and the result (including quota) is displayed

#### Scenario: Save FTP credentials
- **WHEN** the user clicks "Save" on the FTP card
- **THEN** the credentials are sent to `PUT /users/{userId}/api-keys/PUBLISHING` with provider `ftp` and the JSON-encoded credentials as `apiKey`

#### Scenario: Load existing credentials
- **WHEN** the user navigates to Publication Credentials and has existing FTP credentials stored
- **THEN** the form fields are populated (password masked) from the stored values

### Requirement: Per-podcast publication targets configuration
The frontend SHALL provide a "Publication Targets" section within each podcast's settings. This section SHALL list all supported publication targets with:
- A toggle to enable/disable each target
- An "Auto-publish on generate" toggle per target, which controls whether new episodes are published automatically when they reach GENERATED
- Target-specific configuration fields (FTP: remote path; SoundCloud: playlist ID)
- A "Save" button per target

Targets for which the user has not configured credentials SHALL be greyed out with a message: "Configure credentials in Settings first". The auto-publish toggle SHALL be persisted as the `autoPublish` field on the publication-targets PUT request.

#### Scenario: Display targets with credentials configured
- **WHEN** the user views podcast publication targets and has FTP credentials configured
- **THEN** the FTP target is shown as configurable with a remote path field, an enable toggle, and an auto-publish toggle

#### Scenario: Display targets without credentials
- **WHEN** the user views podcast publication targets and has no FTP credentials configured
- **THEN** the FTP target is greyed out with the hint "Configure credentials in Settings first"

#### Scenario: Enable FTP target with auto-publish
- **WHEN** the user enables the FTP target, turns on auto-publish, enters a remote path `/shows/tech/`, and clicks Save
- **THEN** a `PUT /users/{userId}/podcasts/{podcastId}/publication-targets/ftp` request is sent with `{"config": {"remotePath": "/shows/tech/"}, "enabled": true, "autoPublish": true}`

#### Scenario: Disable a target
- **WHEN** the user disables the SoundCloud target toggle and clicks Save
- **THEN** the target's `enabled` field is set to `false` via the PUT endpoint

#### Scenario: Toggle auto-publish off
- **WHEN** the user turns off the auto-publish toggle for the FTP target and clicks Save
- **THEN** the target's `autoPublish` field is set to `false` via the PUT endpoint

#### Scenario: SoundCloud target shows playlist ID
- **WHEN** the user views podcast publication targets and the podcast has a SoundCloud target with `playlistId: "12345"`
- **THEN** the playlist ID is displayed (read-only, since it's auto-managed during publish)

### Requirement: FTP transfer mode selector
The FTP credential form SHALL include a transfer mode selector offering Passive (default) and Active, persisted with the rest of the FTP credentials and repopulated from the stored non-secret credential copy. The selector SHALL indicate that Passive suits most networks and Active is for networks that block outbound data connections.

#### Scenario: Selecting a transfer mode
- **WHEN** the user picks Active in the FTP form and clicks Save
- **THEN** the stored FTP credentials include `transferMode: "ACTIVE"` and reloading the settings page shows Active selected

#### Scenario: Existing credentials without a stored mode
- **WHEN** the user opens the FTP form for credentials saved before the field existed
- **THEN** the selector shows Passive

### Requirement: Connection test result reflects the response body
The FTP connection test SHALL report success or failure based on the `success` field of the test response, not on the HTTP status, and SHALL display the response's `message` on failure. A response of HTTP 200 with `success: false` SHALL be shown to the user as a failed connection.

#### Scenario: Failed connection reported as failed
- **WHEN** the test endpoint returns HTTP 200 with `{"success": false, "message": "Control connection to ftp.example.com:21 timed out..."}`
- **THEN** the user sees a failure notification carrying that message, not a success notification

#### Scenario: Successful connection
- **WHEN** the test endpoint returns HTTP 200 with `{"success": true, "message": "Connected successfully"}`
- **THEN** the user sees a success notification with that message

