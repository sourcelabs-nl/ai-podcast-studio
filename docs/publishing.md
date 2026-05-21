# Publishing and Integrations

This document covers how to set up the integrations that let AI Podcast Studio reach the outside world: FTP and SoundCloud as publication targets, and X (Twitter) and Nitter as content sources.

Episodes can be published to multiple targets after generation. Supported targets: **FTP** and **SoundCloud**. Publication targets are configured per-podcast via the API, and each episode tracks its publication status (PENDING, PUBLISHED, FAILED) independently per target. Episodes can also be unpublished from any target.

## Publishing to FTP

FTP publishing uploads the episode audio file and updates the static `feed.xml` on a remote server. Configure an FTP publication target on a podcast:

```bash
curl -X PUT http://localhost:8085/users/{userId}/podcasts/{podcastId}/publication-targets/ftp \
  -H 'Content-Type: application/json' \
  -d '{
    "host": "ftp.example.com",
    "port": 21,
    "username": "user",
    "password": "pass",
    "useTls": true,
    "remotePath": "/podcast",
    "publicUrl": "https://podcast.example.com"
  }'
```

You can test the FTP connection before publishing:

```bash
curl -X POST http://localhost:8085/users/{userId}/publishing/test/ftp \
  -H 'Content-Type: application/json' \
  -d '{"host": "ftp.example.com", "port": 21, "username": "user", "password": "pass", "useTls": true}'
```

## Publishing to SoundCloud

SoundCloud publishing requires a SoundCloud OAuth app and a connected user account.

1. **Register a SoundCloud app** at https://soundcloud.com/you/apps (you must be logged in to SoundCloud). During registration, set the **redirect URI** to match your app's base URL:

   ```
   http://localhost:8085/oauth/soundcloud/callback
   ```

   The redirect URI must exactly match the `app.feed.base-url` configured in `application.yaml` followed by `/oauth/soundcloud/callback`. If these don't match, you'll get a `redirect_uri_mismatch` error during authorization.

2. **Add credentials** to your `.envrc` file:

   ```bash
   export APP_SOUNDCLOUD_CLIENT_ID=<your-soundcloud-client-id>
   export APP_SOUNDCLOUD_CLIENT_SECRET=<your-soundcloud-client-secret>
   ```

   Then run `direnv allow` to reload.

3. **Restart the app** so it picks up the new environment variables.

4. **Connect your SoundCloud account** via the OAuth flow:

   ```bash
   # Get the authorization URL
   curl http://localhost:8085/users/{userId}/oauth/soundcloud/authorize
   # returns { "authorizationUrl": "https://secure.soundcloud.com/authorize?..." }

   # Copy the authorizationUrl and open it in a browser
   # Log in to SoundCloud and authorize the app
   # The callback redirects back to your app and stores the tokens automatically

   # Verify the connection
   curl http://localhost:8085/users/{userId}/oauth/soundcloud/status
   # returns { "connected": true, ... }
   ```

5. **Publish an episode** (must be in `GENERATED` status):

   ```bash
   curl -X POST http://localhost:8085/users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/publish/soundcloud
   ```

The track is uploaded with the podcast name + date as title, a description from the script, and tags from the podcast topic. Episodes are automatically grouped into a SoundCloud playlist per podcast: on first publish a new playlist is created, and subsequent episodes are added to it. Publication status (PENDING, PUBLISHED, FAILED) is tracked per episode and target.

Before uploading, the system checks the SoundCloud upload quota. If the quota is exceeded, the publish wizard offers to remove the oldest app-uploaded track (filtered by podcast name) to free up space. If the OAuth token has expired, the wizard shows a re-authorize button.

To enable an RSS feed for your SoundCloud uploads, go to your SoundCloud **Settings > Content** tab, find your RSS feed URL, and enable **"Include in RSS feed"** under upload defaults. This lets podcast apps subscribe to your SoundCloud-hosted episodes directly.

## Monitoring X (Twitter) accounts

X accounts can be added as content sources so their posts are included in podcast briefings. This requires an X developer app and a connected user account.

1. **Register an X app** at https://developer.x.com/en/portal/dashboard. Enable OAuth 2.0 with type "Web App" and set the redirect URI to `http://localhost:8085/oauth/x/callback`. Requires at least the Basic tier ($100/month).

2. **Add credentials** to your `.envrc` file:

   ```bash
   export APP_X_CLIENT_ID=<your-x-client-id>
   export APP_X_CLIENT_SECRET=<your-x-client-secret>
   ```

   Then run `direnv allow` to reload.

3. **Restart the app** so it picks up the new environment variables.

4. **Connect your X account** via the OAuth flow:

   ```bash
   # Get the authorization URL
   curl http://localhost:8085/users/{userId}/oauth/x/authorize
   # returns { "authorizationUrl": "https://twitter.com/i/oauth2/authorize?..." }

   # Open the URL in a browser, log in and authorize the app
   # The callback completes automatically and stores your tokens

   # Verify the connection
   curl http://localhost:8085/users/{userId}/oauth/x/status
   ```

5. **Add an X source** to a podcast:

   ```bash
   curl -X POST http://localhost:8085/users/{userId}/podcasts/{podcastId}/sources \
     -H 'Content-Type: application/json' \
     -d '{"type": "twitter", "url": "elonmusk", "pollIntervalMinutes": 60}'
   ```

The `url` field accepts a plain username (e.g., `elonmusk`), `@username`, or a full URL (e.g., `https://x.com/elonmusk`). Posts are polled on the configured interval and included in briefings. X tokens are automatically refreshed (they expire every 2 hours).

## Using Nitter as an alternative to X

If you don't have an X developer account, you can use [Nitter](https://nitter.net) as a free alternative. Nitter is an open-source front-end for Twitter that exposes public RSS feeds, no API key or OAuth setup required. Add a Nitter feed as a regular RSS source:

```bash
curl -X POST http://localhost:8085/users/{userId}/podcasts/{podcastId}/sources \
  -H 'Content-Type: application/json' \
  -d '{"type": "rss", "url": "https://nitter.net/elonmusk/rss", "pollIntervalMinutes": 60}'
```

Nitter sources are automatically detected for aggregation: tweets within a thread (parent + replies) are merged into one article per thread at briefing time, just like native X sources. Note that Nitter coverage may not be fully on par with the X API (e.g., missing replies, retweets, or media context), but it works well for following public accounts without any paid API access.
