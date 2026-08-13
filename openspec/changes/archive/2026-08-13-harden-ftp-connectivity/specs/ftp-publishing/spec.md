## ADDED Requirements

### Requirement: Shared FTP connection factory
The system SHALL provide an `FtpConnectionFactory` component that is the single place where an FTP(S) connection is established. It SHALL accept an `FtpConnectionSettings` value (host, port, username, password, `useTls`, `transferMode`) and return a connected, authenticated client configured for binary transfers. Both `FtpPublisher` and the publishing connection-test service SHALL obtain their clients from this factory, and neither SHALL contain its own connect/login sequence. Callers remain responsible for disconnecting.

#### Scenario: Publisher and test service share one connect path
- **WHEN** an FTP connection is established either for publishing an episode or for a connection test
- **THEN** the same `FtpConnectionFactory` performs the connect, login, transfer-mode selection, and TLS data-channel setup, so a successful connection test implies publishing can connect with the same settings

#### Scenario: Binary transfer mode is set
- **WHEN** the factory returns a connected client
- **THEN** the file type is set to binary, so MP3 uploads are not corrupted by ASCII translation

### Requirement: Verified data channel with transfer-mode fallback
Before a connection is handed to a caller that transfers files, the system SHALL verify that a data connection can be opened, by listing the working directory. If that verification fails, the system SHALL close the connection, reconnect using the opposite transfer mode, and verify again. The connection returned SHALL report which transfer mode succeeded and, when the configured mode was not the one that worked, which mode was tried first and why it failed.

Fallback SHALL apply only to data-channel failures. Failures that a transfer mode cannot influence -- a control connection that times out or is refused, rejected credentials, or failed TLS negotiation -- SHALL propagate without a second attempt. If neither mode can open a data connection, the failure SHALL name both modes and both underlying errors.

Both episode publishing and the connection-test endpoint SHALL use this path, so that testing a connection exercises exactly what publishing does. Operations that use only the control channel, such as deleting a remote file, MAY connect without verification or fallback.

When a transfer proceeds over the fallback mode, the system SHALL log a warning naming both modes, since the stored setting no longer matches the network.

#### Scenario: Configured mode works
- **WHEN** the data channel opens successfully in the configured transfer mode
- **THEN** that connection is used, no fallback is attempted, and no fallback is reported

#### Scenario: Falling back to the other mode before any upload
- **WHEN** the configured mode is passive and the passive data connection cannot be opened, but the server is reachable in active mode
- **THEN** the passive connection is closed, a new connection is made in active mode, and the episode is uploaded over it
- **AND** because verification happens before any file is sent, no partial file is left behind by the abandoned attempt
- **AND** a warning is logged naming passive as the failed mode and active as the one used

#### Scenario: Control connection failure is not retried
- **WHEN** the control connection times out because the FTP port is blocked
- **THEN** the failure is reported immediately without attempting the other transfer mode

#### Scenario: Neither transfer mode can open a data connection
- **WHEN** the data connection fails in both passive and active mode
- **THEN** the failure names both modes and includes the error from each attempt

### Requirement: Configurable FTP transfer mode
The FTP connection settings SHALL include a `transferMode` of `PASSIVE` or `ACTIVE`, which is the mode tried first. `PASSIVE` SHALL be the default and SHALL be used when the stored credentials do not specify a mode. When `PASSIVE` is selected the client SHALL enter local passive mode; when `ACTIVE` is selected it SHALL enter local active mode.

#### Scenario: Passive mode by default
- **WHEN** stored FTP credentials contain no `transferMode` field
- **THEN** the connection uses passive mode, matching the behavior before the field existed

#### Scenario: Active mode selected
- **WHEN** stored FTP credentials specify `transferMode: "ACTIVE"`
- **THEN** the connection enters local active mode, and the server is expected to open the data connection back to the client

### Requirement: Protected data channel for FTPS connections
When `useTls` is enabled, the system SHALL request an unlimited protection buffer size (`PBSZ 0`) and private data-channel protection (`PROT P`) after authenticating, so file transfers occur over an encrypted data channel.

#### Scenario: FTPS data channel is protected
- **WHEN** a connection is established with `useTls = true`
- **THEN** `PBSZ 0` and `PROT P` are issued after login, and transfers to a server that requires data-channel protection succeed instead of failing with `522 SSL/TLS required on the data channel`

#### Scenario: Plain FTP skips data-channel protection
- **WHEN** a connection is established with `useTls = false`
- **THEN** no `PBSZ`/`PROT` commands are issued

### Requirement: NAT-tolerant passive connections and transfer timeouts
The system SHALL configure FTP connections so that servers behind NAT and long-running uploads do not fail spuriously: extended passive mode SHALL be disabled for IPv4, remote verification of the data connection host SHALL be disabled so a passive reply advertising a private address is still usable, a data-channel timeout SHALL be set so a blocked data port fails rather than hanging indefinitely, and a control keep-alive SHALL be sent during long transfers.

#### Scenario: Server advertises a private address in its passive reply
- **WHEN** a server behind NAT answers `PASV` with an unroutable private IP address
- **THEN** the client still opens the data connection, rather than refusing it because the advertised host differs from the control host

#### Scenario: Data port silently dropped
- **WHEN** the network drops packets to the passive data port
- **THEN** the transfer fails within the data timeout with a data-channel error rather than hanging until the process is interrupted

### Requirement: FTP failures are classified by connection phase
FTP connection and transfer failures SHALL produce an error message naming the phase that failed: control connection, authentication, TLS negotiation, or data channel. Authentication and data-channel failures SHALL include the server's reply text. When the control connection times out or is refused, the message SHALL name the host and port and SHALL state that the port is likely blocked by the network, since no client-side setting can recover from that.

#### Scenario: Control port blocked by the network
- **WHEN** the control connection to the configured host and port times out
- **THEN** the failure message names the host and port and indicates the FTP port is likely blocked by the network, instead of reporting only "Connect timed out"

#### Scenario: Authentication rejected
- **WHEN** the server rejects the username and password
- **THEN** the failure identifies authentication as the failing phase and includes the server's reply text

#### Scenario: Upload rejected by the server
- **WHEN** the connection succeeds but a file upload is rejected
- **THEN** the failure identifies the file and includes the server's reply text, so reply codes such as `522` and `425` are visible in the log
