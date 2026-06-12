## MODIFIED Requirements

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
