## 1. Deployment

- [x] 1.1 Add `docker/nitter/docker-compose.yml` with Nitter and Redis, loopback-bound on 8081, unprivileged and read-only, with healthchecks.
- [x] 1.2 Pin `zedeus/nitter:latest-arm64` for Apple Silicon and note the x86_64 tag.
- [x] 1.3 Add `docker/nitter/nitter.conf`: `redisHost = nitter-redis`, user-tweet RSS only, `maxConcurrentReqs = 2`, media proxying off, `hmacKey` placeholder.
- [x] 1.4 Verify the compose file parses and the ARM64 image pulls.

## 2. Credentials

- [x] 2.1 Exclude `docker/nitter/sessions.jsonl` from version control.
- [x] 2.2 Document the burner-account requirement, phone verification, and the ToS/lockout expectation.
- [x] 2.3 Document both session tools, leading with the browser-driven one since the direct flow is intermittently Cloudflare-blocked.
- [ ] 2.4 **Operator step:** create the burner account, set `hmacKey`, generate `sessions.jsonl`.

## 3. Repointing

- [x] 3.1 Add `scripts/repoint-nitter-sources.sh` rewriting URLs through the REST API, discovering user and podcast ids.
- [x] 3.2 Re-send every source field, since the update endpoint replaces configuration wholesale.
- [x] 3.3 Support `--dry-run`, and treat "nothing to repoint" as success.
- [x] 3.4 Verify the dry run against the running app (36 sources matched).

## 4. Verification

- [ ] 4.1 Start the containers and confirm `http://localhost:8081/sama/rss` returns items. Blocked on 2.4.
- [ ] 4.2 Run the repoint script for real and confirm from `app.log` that the sources save posts. Blocked on 4.1.
- [ ] 4.3 Confirm the next episode's article count returns toward the 22-38 baseline. Blocked on 4.2.
