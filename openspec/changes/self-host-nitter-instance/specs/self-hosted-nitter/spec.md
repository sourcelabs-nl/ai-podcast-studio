## ADDED Requirements

### Requirement: Nitter runs locally and is not publicly reachable

The Nitter deployment SHALL bind only to the loopback interface, SHALL run as a non-root user with a read-only root filesystem and no added capabilities, and SHALL NOT be exposed to the public internet.

Public Nitter instances are what X's countermeasures target, and a publicly reachable instance also puts the burner account's session in front of arbitrary traffic. This is a personal-scale deployment serving one application on the same machine.

#### Scenario: Port binding is loopback-only
- **WHEN** the compose file maps the Nitter container's port
- **THEN** it binds to `127.0.0.1` rather than all interfaces

#### Scenario: Container runs unprivileged
- **WHEN** the Nitter and Redis containers start
- **THEN** both run as a non-root user, with a read-only root filesystem, `no-new-privileges`, and all capabilities dropped

### Requirement: Only user-tweet RSS feeds are enabled

The Nitter configuration SHALL enable RSS and user-tweet feeds, and SHALL disable replies, media, search and list feeds. Concurrency SHALL be limited so a single session is not rate-limited.

The application reads only `/{user}/rss`. Every other surface is unused attack surface and unnecessary load on a session that X will rate-limit.

#### Scenario: User tweet feeds are served
- **WHEN** the application requests `/{user}/rss`
- **THEN** Nitter returns that account's timeline as RSS

#### Scenario: Unused feed types are disabled
- **WHEN** the configuration is applied
- **THEN** `enableRSSUserReplies`, `enableRSSUserMedia`, `enableRSSSearch` and `enableRSSList` are all false

#### Scenario: Concurrency is bounded
- **WHEN** the configuration is applied
- **THEN** `maxConcurrentReqs` is small enough that 35 feeds polled every 30 minutes does not trip X's rate limits on one session

### Requirement: Session credentials are operator-supplied and never committed

The session file SHALL be created by the operator from their own burner account credentials and SHALL be excluded from version control. The deployment SHALL NOT start without it.

The file contains a logged-in X session equivalent to an account password. Generating it requires credentials that belong to the operator, so it is a manual step by design rather than an automated one.

#### Scenario: Session file is git-ignored
- **WHEN** a session file exists in the Nitter directory
- **THEN** git does not track it

#### Scenario: Missing session prevents startup
- **WHEN** the containers are started with no session file present
- **THEN** Nitter does not serve feeds, rather than starting and silently returning empty results

#### Scenario: Multiple sessions are supported
- **WHEN** one session begins hitting rate limits
- **THEN** further sessions can be appended to the same line-delimited file without reconfiguration

### Requirement: Sources are repointed through the REST API

Rewriting source URLs SHALL go through the application's REST API rather than direct database access, SHALL preserve each source's id and remaining configuration, and SHALL support a dry run.

Preserving ids keeps each source's article history and its links to past episodes intact. The update endpoint replaces a source's configuration wholesale, so every field must be re-sent or it silently reverts to its default.

#### Scenario: Only the host changes
- **WHEN** a source at `https://nitter.net/sama/rss` is repointed to a base of `http://localhost:8081`
- **THEN** its URL becomes `http://localhost:8081/sama/rss` and its id, poll interval, label and other settings are unchanged

#### Scenario: Dry run changes nothing
- **WHEN** the repoint script runs with `--dry-run`
- **THEN** it reports the intended changes and no source is modified

#### Scenario: Repointing clears the failure backoff
- **WHEN** sources that had accumulated consecutive failures are repointed
- **THEN** their failure counters are cleared, so they poll on their normal interval rather than their accumulated backoff

#### Scenario: Identifiers are discovered, not hardcoded
- **WHEN** the script runs against a restored database with different user and podcast ids
- **THEN** it resolves them from the API rather than failing on stale identifiers

#### Scenario: Nothing to do is not an error
- **WHEN** the script runs and no `nitter.net` sources remain
- **THEN** it reports that and exits successfully

### Requirement: Instance failure is visible, not silent

An outage of the self-hosted instance SHALL surface through the host circuit breaker rather than silently reducing episode content.

This deployment is expected to break periodically when X changes its internals. The failure mode that caused this work — 35 sources dying unnoticed for four days — must not repeat.

#### Scenario: A dead instance opens the breaker
- **WHEN** the self-hosted instance stops serving feeds and its sources fail permanently
- **THEN** the host's circuit breaker opens and logs the transition and the suppressed source count

#### Scenario: Recovery resumes polling automatically
- **WHEN** the instance is restored
- **THEN** the breaker's half-open probe succeeds, the breaker closes, and the host's sources resume on their normal interval
