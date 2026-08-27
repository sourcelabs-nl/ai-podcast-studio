## Why

`nitter.net` was decommissioned upstream in August 2026. It now returns an empty HTTP 200 to browsers and 403 to this application, so the 35 X/Twitter accounts the podcast follows have produced nothing since 2026-08-20. They were roughly 60% of daily article intake, and the 2026-08-24 episode was built from 9 distinct articles across 3 hosts against a 22-38 baseline.

The accounts matter disproportionately: `@OpenAI`, `@AnthropicAI`, `@GoogleDeepMind`, `@sama` and `@karpathy` announce on X first, and the RSS feeds that remain pick those stories up hours later, if at all.

Nitter itself is still maintained (the upstream image was rebuilt the same week this was written), but public instances are no longer viable: X removed guest accounts in 2024, so an instance now needs a logged-in session from a real account. Running one privately, at personal scale, is the remaining way to read those feeds without the paid X API, which since 2026-02-06 has no free tier and bills new developers per call.

## What Changes

- **New:** `docker/nitter/` containing a `docker-compose.yml` (Nitter + Redis, bound to localhost only, running unprivileged and read-only) and a `nitter.conf` tuned for this use: RSS user-tweet feeds only, `maxConcurrentReqs = 2`, no media proxying.
- **New:** `scripts/repoint-nitter-sources.sh`, which rewrites every `nitter.net` source URL to the self-hosted base through the application's REST API. It discovers the user and podcast rather than hardcoding ids, supports `--dry-run`, and re-sends every field because the update endpoint replaces a source's config wholesale.
- **New:** `docker/nitter/README.md` covering the burner-account requirement, the session-token step, verification, and what to do when X breaks Nitter again.
- **Changed:** `.gitignore` excludes `docker/nitter/sessions.jsonl`, which holds a logged-in X session.

Operational, not application code: no Kotlin changes and no schema changes. The 35 sources keep their existing ids, so their article history is preserved.

**Requires a manual step.** The session token is generated from credentials that belong to the operator, so `sessions.jsonl` is created by hand and the container will not start without it.

## Capabilities

### New Capabilities

- `self-hosted-nitter`: the Nitter deployment, its configuration constraints, the credential handling, and the source-repointing procedure.

### Modified Capabilities

None. Sources remain ordinary RSS sources; only their URLs change.

## Impact

**Files**
- `docker/nitter/docker-compose.yml`, `docker/nitter/nitter.conf`, `docker/nitter/README.md` (new)
- `scripts/repoint-nitter-sources.sh` (new)
- `.gitignore`

**Operational**
- Requires a burner X account with phone verification, and accepts that automated reading is against X's ToS: the account is expected to be locked periodically and replaced.
- Adds a maintenance obligation. X rotates its GraphQL endpoint hashes every few weeks; recovery is an image pull, or a fresh session if the account rather than the code expired.
- The host circuit breaker added in `add-source-host-circuit-breaker` covers this deployment: all 35 sources share one host, so an outage opens the breaker and logs it rather than silently thinning the episode.

**Cost**
- No monetary cost, versus roughly $25-50/month for the equivalent volume on X's pay-per-use API.
