## Context

`FtpPublisher` builds and connects its own `FTPClient`/`FTPSClient` in a private `connect()` (`FtpPublisher.kt:197`): connect timeout 15s, login, `enterLocalPassiveMode()`, binary file type. `PublishingTestService.testFtp` repeats the same sequence with a 10s timeout and a `listFiles("/")`. Credentials come from an encrypted JSON blob in `user_provider_configs` (category `PUBLISHING`, provider `ftp`), parsed into `FtpCredentials`, which is declared at the bottom of `FtpPublisher.kt`. The frontend stores the same JSON minus the password in `base_url` so the form can be repopulated.

The observed failure on the reporting user's network is a blocked control port: ports 21, 990 and 22 all time out to multiple unrelated hosts while 443 is open, and no client-side proxy is configured. That failure is not fixable in this codebase, but it is currently indistinguishable from a wrong host, a firewalled data port, or a TLS problem.

commons-net 3.11.1 is the FTP client. It provides `enterLocalActiveMode`, `setDataTimeout(Duration)`, `setRemoteVerificationEnabled`, `setUseEPSVwithIPv4`, `setControlKeepAliveTimeout`, and on `FTPSClient` `execPBSZ`/`execPROT`.

## Goals / Non-Goals

**Goals:**
- One connect path shared by publishing and connection testing, so a passing test means publishing can connect.
- Make the FTPS data channel actually protected, and make NAT'd passive servers work.
- Turn every connection failure into a message that names the phase and, where possible, the likely cause.
- Let a user choose a preferred transfer mode, and keep publishing working when that mode is wrong for the network.

**Non-Goals:**
- Making FTP work through a blocked control port. No client setting can do this; it needs a transport over a permitted port, which is a separate change.
- Adding proxy support. The affected network has no client-configurable proxy (`scutil --proxy` is empty), so it would not help the case that prompted this.
- Working around FTPS session reuse (`require_ssl_reuse`, seen as `425 Unable to build data connection`). The fix requires reflection into `sun.security.ssl`, which the module system blocks on this project's JDK 25; commons-net 3.11.1 has no supported alternative. If a server needs it, the options are `useTls = false` or a server-side setting.
- Falling back on anything other than a data-channel failure. A blocked control port, bad credentials, or a TLS mismatch fail identically in either transfer mode, so retrying only doubles the wait.

## Decisions

**A single `FtpConnectionFactory` returning a connected client.** The alternative, a shared settings object with each caller doing its own connect, leaves the drift problem in place. The factory takes an `FtpConnectionSettings` (host, port, username, password, useTls, transferMode) and returns a logged-in, configured `FTPClient`; callers keep their existing `finally { disconnect() }`. Client construction is injected as a `(Boolean) -> FTPClient` constructor default so tests can supply a MockK client without a live server.

**`transferMode` in the credential JSON, not a new column.** The credential blob is already schemaless JSON, and the field is a property of how to reach that server, so it belongs with host/port/useTls. Absent field parses to `PASSIVE`, matching today's hardcoded behavior, so no migration or backfill is needed. It is also mirrored into the non-secret `base_url` copy so the settings form can show the current value.

**Robustness settings applied unconditionally rather than exposed as toggles.** `PROT P` on FTPS, the data timeout, EPSV-off-on-IPv4, and disabled remote verification are all cases where the current default is wrong more often than right, and each extra toggle is another thing a user must guess at. Only transfer mode, which genuinely depends on the network and cannot be inferred, becomes a setting.

**Remote data-connection verification is disabled.** This is the check that the passive data connection goes to the same host as the control connection; disabling it is what makes NAT'd servers work, at the cost of a spoofing check that only matters against an attacker who can already inject into the control channel. commons-net's NAT workaround already substitutes the control host when the PASV reply is unroutable, so this is the belt-and-braces half of the same fix.

**Errors classified by phase, with a network hint for control-connection timeouts.** A `SocketTimeoutException`/`ConnectException` while opening the control socket produces a message naming the host and port and stating the port is likely blocked, since that is what a silent drop means. Authentication failures report the server's reply string rather than a bare "Authentication failed". Data-channel failures carry the reply code, so `522` (TLS required) and `425` (data connection refused) are distinguishable in `app.log`.

**The configured mode is a preference, not a constraint: both publishing and the test endpoint fall back to the other mode.** The stored `transferMode` is tried first; if only the data channel is the problem, the other mode is tried before any file moves.

What makes that safe is verifying the data channel with a directory listing immediately after login, inside `connectWithFallback`. Listing is idempotent, so the fallback decision is taken before an upload exists to corrupt. The alternative, falling back when a real `storeFile` fails, would mean deciding after a partial file already sits on the server, with recovery depending on the retry succeeding. The cost is one `LIST` per connection, negligible against an MP3 upload.

Because the probe lives in the factory, `PublishingTestService` has no probing logic of its own: testing a connection is now literally "make the connection publishing would make", which is the strongest form of the shared-path goal. `deleteRemoteFile` uses plain `connect` instead, since `DELE` is a control-channel command that needs no data connection.

A fallback leaves the stored setting wrong, so it is surfaced rather than hidden: publishing logs a warning naming both modes, and the test endpoint reports success while telling the user which mode to save.

**The frontend must read `success`, not the HTTP status.** The endpoint deliberately returns 200 with `success: false` for a failed connection (it is a successful test of a broken connection, not a server error). The current code treats 200 as connected, which is why a blocked port was reported as a successful connection.

## Risks / Trade-offs

- **Disabled remote verification** weakens a data-connection anti-spoofing check, as above. Accepted because NAT'd FTP servers are common and the threat model requires an already-privileged attacker.
- **Active mode will usually fail behind NAT**, since it asks the server to connect back to the client. Exposing it invites confused reports; mitigated by the fallback trying it automatically and reporting which mode actually worked rather than leaving the user to guess.
- **A fallback doubles the worst-case connection time** (two connect-and-verify attempts) and adds one `LIST` to every successful connection. The doubling only happens on the data-channel failure path, and both halves are bounded by the connect and data timeouts.
- **A publish that succeeds via fallback hides a wrong setting** behind a log warning. Accepted deliberately: an episode publishing successfully beats failing to teach the user about transfer modes, and the connection test states the correct value plainly.
- **Not verified against a live FTPS server on a permissive network.** The originating network blocks port 21 to every host tried, so `PROT P`, the NAT settings, and active mode are covered by unit tests against a mocked client, not by a real transfer. The blocked-port path itself is verified end to end.
