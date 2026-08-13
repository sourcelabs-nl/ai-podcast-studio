## 1. Connection types

- [x] 1.1 Add `FtpTransferMode` enum (`PASSIVE`, `ACTIVE`) and `FtpConnectionSettings` data class to `PublishingTypes.kt`
- [x] 1.2 Move `FtpCredentials` out of `FtpPublisher.kt`, replacing it with `FtpConnectionSettings` so one type describes an FTP connection
- [x] 1.3 Add optional `transferMode` to `FtpTestCredentials`, defaulting to `PASSIVE`
- [x] 1.4 Add an `FtpConnection` type carrying the connected client, the mode that worked, and the mode fallen back from

## 2. FtpConnectionFactory

- [x] 2.1 New `FtpConnectionFactory` component with an `internal connect(settings, client)` overload as the testing seam, so tests can supply a mock client without Spring having to inject a function type
- [x] 2.2 Connect with a connect timeout, verify the greeting reply, and log in; classify failures by phase via a dedicated exception type
- [x] 2.3 Issue `PBSZ 0` and `PROT P` when `useTls` is true
- [x] 2.4 Apply the selected transfer mode (`enterLocalPassiveMode` / `enterLocalActiveMode`)
- [x] 2.5 Set binary file type, data timeout, control keep-alive, EPSV-with-IPv4 off, remote verification off
- [x] 2.6 Map a timed-out or refused control connection to a message naming host/port and the likely network block

## 3. Data-channel verification and transfer-mode fallback

- [x] 3.1 `verifyDataChannel` lists the working directory and raises a `DATA_CHANNEL` failure on an error or a non-positive reply
- [x] 3.2 `connectWithFallback` tries the configured mode, then the opposite mode on a data-channel failure, returning an `FtpConnection` naming the mode used and the mode fallen back from
- [x] 3.3 Close the abandoned connection before retrying, and propagate control/auth/TLS failures without a second attempt
- [x] 3.4 Report a both-modes failure with the error from each attempt

## 4. Wire up callers

- [x] 4.1 `FtpPublisher` resolves `transferMode` from the credential JSON and obtains its client from the factory; remove its private `connect()`
- [x] 4.2 Both upload paths (`publish`, `postPublish`) use `connectWithFallback` and log a warning when the fallback was needed
- [x] 4.3 `deleteRemoteFile` uses plain `connect`, since `DELE` needs no data channel
- [x] 4.4 Include the server reply text in upload failure messages
- [x] 4.5 `PublishingTestService.testFtp` delegates to `connectWithFallback` and reports which mode worked; it keeps no probing logic of its own
- [x] 4.6 Confirm no controller changes are needed beyond passing `transferMode` through

## 5. Frontend

- [x] 5.1 Add `transferMode` to the FTP form state, defaults, and load-from-stored-config path
- [x] 5.2 Add the transfer mode selector to the FTP card with a short explanatory hint
- [x] 5.3 Fix `handleTestFtp` to read `success`/`message` from the response body instead of assuming HTTP 200 means connected

## 6. Tests

- [x] 6.1 Factory: passive vs active mode selection
- [x] 6.2 Factory: `PBSZ`/`PROT` issued for an `FTPSClient` (the plain-`FTPClient` case needs no test: those methods only exist on `FTPSClient`, so the type check makes it unreachable)
- [x] 6.3 Factory: authentication failure includes the reply text and names the phase
- [x] 6.4 Factory: control-connection timeout produces the blocked-port message; unresolvable host and TLS failures are classified too
- [x] 6.5 Factory: working data channel needs no fallback; broken data channel falls back and closes the abandoned connection
- [x] 6.6 Factory: control-connection failure is not retried; both-modes failure names both attempts
- [x] 6.7 Test service: plain success, fallback success naming the mode, and phase-classified failure
- [x] 6.8 Update `FtpPublisherTest` and `PublishingTestControllerTest` for the new constructor/fields
- [x] 6.9 Full `mvn test` suite green (959 tests)

## 7. Verification

- [x] 7.1 Code review pass (`/code-review`) on the pre-fallback state: clean (0 violations, 2 informational notes)
- [x] 7.2 Re-run the code review over the fallback additions: clean (0 violations, 0 warnings, 3 notes); applied the one actionable note by separating the two attempt reasons in the both-modes-failed message
- [x] 7.3 Restart the app and confirm the FTP test endpoint returns the phase-classified blocked-port message on this network, with no pointless fallback attempt
- [x] 7.4 Confirm the endpoint returns HTTP 200 with `success: false` and that the settings page now branches on that flag (code + build verified; not clicked through in a browser)
- [x] 7.5 `npm run build` (or lint) clean for the frontend change
