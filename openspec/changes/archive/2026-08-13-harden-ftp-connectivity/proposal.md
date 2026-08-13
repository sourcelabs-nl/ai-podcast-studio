## Why

FTP publishing fails on restricted networks and the failures are hard to act on. A measured example: on a network that drops outbound port 21, `POST /users/{id}/publishing/test/ftp` returns `{"success": false, "message": "Connect timed out"}` after 10 seconds, which says nothing about which phase failed or that the network is the cause. Worse, the settings page ignores the `success` flag entirely (it only checks `res.ok`), so a failed test is reported to the user as "FTP connection successful."

Alongside the diagnostics gap, the connection setup itself has real defects that only surface on some networks:

- `FTPSClient` is used without `PBSZ 0` / `PROT P`, so the data channel is never switched to protected. Servers that require it reject transfers (`522 SSL/TLS required on the data channel`).
- Passive mode is hardcoded, with no way to fall back to active mode on a network that permits it.
- No data-channel timeout is set, so a blocked passive port tends to hang instead of failing with a usable error.
- EPSV is left enabled on IPv4, and remote verification is left on, so a server behind NAT that advertises a private address in its PASV reply has its data connection refused by the client.
- The connect/login logic is duplicated between `FtpPublisher.connect` (`FtpPublisher.kt:197`) and `PublishingTestService.testFtp`, so "Test Connection" does not exercise what publishing actually does, and the two can drift.

## What Changes

- Add a `FtpConnectionFactory` component owning a single connect path: client construction, timeouts, TLS data-channel protection, transfer mode, and NAT-tolerant passive settings. `FtpPublisher` and `PublishingTestService` both use it, removing the duplicated logic.
- Add a `transferMode` field (`PASSIVE` default, or `ACTIVE`) to the stored FTP credential JSON, selectable in the publishing settings form. Existing credential blobs without the field keep passive behavior.
- For FTPS connections, issue `PBSZ 0` and `PROT P` so the data channel is encrypted.
- Set a data-channel timeout and a control keep-alive so long MP3 uploads survive idle-timeout firewalls and blocked data ports fail rather than hang.
- Disable EPSV-with-IPv4 and remote data-connection verification so servers behind NAT work.
- Classify connection failures by phase (DNS / control connection / authentication / TLS / data channel) and return a message naming the phase, the server's reply where there is one, and for a timed-out control connection the likely cause (the network blocking the FTP port).
- Treat the configured transfer mode as a preference: verify the data channel with a directory listing right after login, and if only that fails, reconnect in the other mode before any file is transferred. Publishing and the test endpoint share this path, so a fallback keeps publishing working instead of failing, and never abandons a half-written upload. Publishing logs a warning naming both modes, and the test endpoint reports which mode worked so the stored setting can be corrected.
- Fix the settings page to read `success` and `message` from the test response instead of assuming HTTP 200 means connected.
- Keep `scripts/ftp-diagnose.py`, the standalone probe used to diagnose the blocked-port case (DNS, port reachability, greeting, AUTH TLS, PASV target reachability, PORT acceptance) from outside the application.

**Not addressed:** none of this makes FTP work on a network that blocks port 21 outright. That needs a different transport (an HTTPS-based publishing target) or a server listening on a permitted port, both out of scope here. This change makes such a network diagnose itself in one click instead of one opaque timeout.

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
- `ftp-publishing`: connection establishment becomes a shared, configurable, NAT- and TLS-correct path with phase-classified errors, instead of hardcoded passive mode inline in the publisher.
- `publishing-credentials`: the FTP credential JSON gains `transferMode`, and the test endpoint's failure messages gain phase classification plus transfer-mode probing.
- `frontend-publication-settings`: the FTP form gains a transfer mode selector, and the test result reflects the response's `success` flag rather than the HTTP status.

## Impact

- `src/main/kotlin/com/aisummarypodcast/publishing/FtpConnectionFactory.kt` (new)
- `src/main/kotlin/com/aisummarypodcast/publishing/FtpPublisher.kt` (delegate connect; `FtpCredentials` moves out of this file)
- `src/main/kotlin/com/aisummarypodcast/publishing/PublishingTestService.kt` (delegate connect, add mode probe)
- `src/main/kotlin/com/aisummarypodcast/publishing/PublishingTypes.kt` (`FtpTransferMode`, `FtpConnectionSettings`, `transferMode` on `FtpTestCredentials`)
- `frontend/src/app/settings/page.tsx` (transfer mode selector, honest test result)
- `scripts/ftp-diagnose.py` (new, standalone network probe)
- No database migration: `transferMode` lives inside the existing encrypted credential JSON blob.
