# Self-hosted Nitter

Reads the 35 X/Twitter accounts this podcast follows. `nitter.net` was decommissioned upstream in
August 2026 and now answers 403 to the app, which silently removed roughly 60% of daily article
intake before anyone noticed.

## What you need first

- **A burner X account.** Not your own account: automated reading is against X's ToS and these
  accounts get locked periodically. Sign up at <https://x.com/i/flow/signup>. X will almost
  certainly demand phone verification, and rejects most VoIP numbers.
- **2FA is optional but recommended.** The tooling supports a TOTP secret, and accounts with 2FA
  enabled are reported to survive longer before being locked behind a captcha.
- **Python 3** for the one-off session step.

## 1. Set the HMAC key

`nitter.conf` ships with `hmacKey = "CHANGE_ME"`. Replace it:

```bash
openssl rand -hex 32
```

## 2. Create the session token

Nitter needs a logged-in session; guest accounts were removed by X in 2024. The browser-driven
tool is the current one, since the older API flow is intermittently blocked by Cloudflare:

```bash
git clone --depth 1 https://github.com/zedeus/nitter /tmp/nitter
cd /tmp/nitter
pip3 install -r tools/requirements.txt

# accounts.json: [{"username": "burner", "password": "...", "totp": "TOTP_SECRET_OR_EMPTY"}]
python3 tools/create_sessions_browser.py accounts.json --append sessions.jsonl
```

Copy the resulting `sessions.jsonl` next to this README. It is line-delimited, so you can append
more accounts later if one session starts hitting rate limits.

**Do not commit `sessions.jsonl`.** It is credentials. `.gitignore` covers it.

If the browser tool fails, the older direct flow still exists:

```bash
pip3 install pyotp requests cloudscraper
python3 tools/get_session.py <username> <password> <totp-secret-or-000000> sessions.jsonl
```

## 3. Start it

```bash
docker compose -f docker/nitter/docker-compose.yml up -d
curl -s http://localhost:8081/sama/rss | head -20
```

A feed with `<item>` elements means it works. An empty body or an HTML error page means the
session was rejected — check `docker compose -f docker/nitter/docker-compose.yml logs nitter`.

## 4. Point the app at it

```bash
./scripts/repoint-nitter-sources.sh http://localhost:8081
```

This rewrites each source's URL from `https://nitter.net/<user>/rss` to
`http://localhost:8081/<user>/rss` through the app's REST API. Updating a source through the API
also clears its failure counters, so the 35 sources resume polling on their normal 30-minute
interval rather than their accumulated 24-hour backoff.

## When it breaks

It will. X rotates its GraphQL endpoint hashes and tightens Cloudflare rules every few weeks, and
upstream patches Nitter in response — the image was rebuilt the same week this was written.

The app now notices on its own: all 35 sources share one host, so the circuit breaker opens after
three permanent failures and logs

```
[Resilience] Circuit breaker 'localhost' CLOSED -> OPEN
[Polling] Host localhost looks structurally down — skipped 35 of 35 due sources
```

instead of silently thinning the episode. To recover:

```bash
docker compose -f docker/nitter/docker-compose.yml pull
docker compose -f docker/nitter/docker-compose.yml up -d
```

If the session itself expired rather than the code breaking, redo step 2.
