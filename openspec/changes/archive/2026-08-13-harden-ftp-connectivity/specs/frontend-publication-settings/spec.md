## ADDED Requirements

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
