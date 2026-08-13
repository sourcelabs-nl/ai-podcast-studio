## MODIFIED Requirements

### Requirement: Publication credential storage
The system SHALL store publication provider credentials in the `user_provider_configs` table under category `PUBLISHING`. The `encrypted_api_key` column SHALL contain an encrypted JSON string with provider-specific fields:
- Provider `ftp`: `{"host": "...", "port": 21, "username": "...", "password": "...", "useTls": true, "transferMode": "PASSIVE"}`
- Provider `soundcloud`: `{"clientId": "...", "clientSecret": "...", "callbackUri": "..."}`

The FTP `transferMode` field SHALL be optional and SHALL accept `PASSIVE` or `ACTIVE`; when absent or unrecognized it SHALL be treated as `PASSIVE`. No migration is required for credential blobs stored before the field existed.

The `base_url` column SHALL be null for publishing providers (not meaningful).

#### Scenario: Store FTP credentials
- **WHEN** a `PUT /users/{userId}/api-keys/PUBLISHING` request is received with `{"provider": "ftp", "apiKey": "{\"host\":\"ftp.example.com\",\"port\":21,\"username\":\"user\",\"password\":\"pass\",\"useTls\":true,\"transferMode\":\"PASSIVE\"}"}`
- **THEN** the JSON is encrypted and stored in `user_provider_configs` with category `PUBLISHING` and provider `ftp`

#### Scenario: FTP credentials stored without a transfer mode
- **WHEN** stored FTP credentials contain no `transferMode` field
- **THEN** the connection uses passive mode and no error is raised

#### Scenario: Store SoundCloud credentials
- **WHEN** a `PUT /users/{userId}/api-keys/PUBLISHING` request is received with `{"provider": "soundcloud", "apiKey": "{\"clientId\":\"abc\",\"clientSecret\":\"xyz\",\"callbackUri\":\"https://example.com/callback\"}"}`
- **THEN** the JSON is encrypted and stored with category `PUBLISHING` and provider `soundcloud`

#### Scenario: List publishing credentials
- **WHEN** a `GET /users/{userId}/api-keys` request is received for a user with FTP and SoundCloud publishing credentials
- **THEN** both are included in the response with category `PUBLISHING` -- without exposing the encrypted values

### Requirement: Test connection endpoint
The system SHALL provide a `POST /users/{userId}/publishing/test/{target}` endpoint that tests connectivity for a publication target without persisting credentials. The response SHALL include `success` (boolean) and `message` (String). A failed connection SHALL be reported as HTTP 200 with `success: false`, not as an error status.

For target `ftp`: the request body SHALL contain `{"host": "...", "port": 21, "username": "...", "password": "...", "useTls": true, "transferMode": "PASSIVE"}`. The endpoint SHALL make the same connection publishing makes, through the shared connection factory, including its data-channel verification and transfer-mode fallback. On success, return `{"success": true, "message": "Connected successfully"}`. On failure, return `{"success": false, "message": "<phase-classified error detail>"}`.

When the connection succeeded only after falling back to the opposite transfer mode, the response SHALL report success and name the mode that worked, so the user can correct the stored setting.

For target `soundcloud`: no request body is needed. The endpoint SHALL verify the user's existing OAuth connection by calling the SoundCloud `/me` API and return quota information. On success, return `{"success": true, "message": "Connected as <username>", "quota": {"uploadSecondsUsed": ..., "uploadSecondsLeft": ...}}`. On failure, return `{"success": false, "message": "<error detail>"}`.

#### Scenario: Test FTP connection success
- **WHEN** a `POST /users/{userId}/publishing/test/ftp` request is received with valid FTP credentials
- **THEN** the system connects, authenticates, and returns `{"success": true, "message": "Connected successfully"}`

#### Scenario: Test FTP connection failure -- control port blocked
- **WHEN** a `POST /users/{userId}/publishing/test/ftp` request is received for a host whose FTP port is unreachable
- **THEN** the system returns HTTP 200 with `success: false` and a message naming the host and port and indicating the port is likely blocked by the network

#### Scenario: Test FTP connection failure -- wrong credentials
- **WHEN** a `POST /users/{userId}/publishing/test/ftp` request is received with wrong username/password
- **THEN** the system returns `{"success": false, "message": "..."}` identifying authentication as the failing phase and including the server's reply

#### Scenario: Test FTP succeeds only in the other transfer mode
- **WHEN** a `POST /users/{userId}/publishing/test/ftp` request is received with `transferMode: "PASSIVE"` and the passive data connection fails, but the same server is reachable in active mode
- **THEN** the endpoint falls back to active mode and returns `success: true` with a message naming active mode as the mode that worked and reporting why passive failed

#### Scenario: Test FTPS connection
- **WHEN** a `POST /users/{userId}/publishing/test/ftp` request is received with `useTls: true`
- **THEN** the system tests using FTPS (explicit TLS) with a protected data channel

#### Scenario: Test SoundCloud connection success
- **WHEN** a `POST /users/{userId}/publishing/test/soundcloud` request is received for a user with a valid OAuth connection
- **THEN** the system returns `{"success": true, "message": "Connected as <username>", "quota": {...}}`

#### Scenario: Test SoundCloud connection -- no OAuth connection
- **WHEN** a `POST /users/{userId}/publishing/test/soundcloud` request is received for a user without a SoundCloud OAuth connection
- **THEN** the system returns `{"success": false, "message": "No SoundCloud connection. Please authorize first."}`

#### Scenario: Test SoundCloud connection -- expired token
- **WHEN** a `POST /users/{userId}/publishing/test/soundcloud` request is received and the OAuth token is expired and refresh fails
- **THEN** the system returns `{"success": false, "message": "SoundCloud authorization expired. Please re-authorize."}`

#### Scenario: Unsupported target
- **WHEN** a `POST /users/{userId}/publishing/test/youtube` request is received
- **THEN** the system returns HTTP 400 with an error indicating the target is not supported
