# AI Podcast Studio

Self-hosted pipeline that monitors content sources (websites, RSS feeds, X accounts), filters and summarizes relevant content using an LLM, converts the summaries to audio via TTS, and delivers them as a podcast feed consumable by any podcast app.

Hear it in action: [The Agentic AI Podcast on Spotify](https://open.spotify.com/show/3sNWski1Zw9mGauajOdToS?si=ebd2ba77b3dc4f38), a daily briefing produced entirely by this project.

## How It Works

### The big picture

You point the app at a handful of websites, RSS feeds, and X accounts that you care about. In the background it keeps an eye on them and collects new posts as they appear. On a schedule you choose (say, every morning at 6), it reads through everything new, decides what's actually worth talking about, writes a podcast script in your preferred style, optionally pauses for you to review/edit it, records it as audio, and publishes the episode so any podcast app can subscribe. You can listen to the finished episode straight from the dashboard.

```mermaid
flowchart LR
    H1(("You")):::human -->|configure sources,<br/>topic, schedule, style| A["Your sources<br/>(websites, RSS, X)"]
    A --> B[("Collected<br/>posts")]
    B --> C["Pick what's<br/>worth covering"]
    C --> D["Write the<br/>script"]
    D --> R{"Require<br/>review?"}
    R -->|"yes"| H2(("You")):::human
    H2 -->|"edit / approve / discard"| E["Record the<br/>audio"]
    R -->|"no"| E
    E --> F["Publish<br/>(RSS, FTP, SoundCloud)"]
    F --> H3(("You")):::human
    H3 -->|"listen in the dashboard<br/>or any podcast app"| F

    classDef human fill:#fef3c7,stroke:#d97706,stroke-width:2px,color:#92400e
```

If anything goes wrong partway through, the app remembers exactly where it got stuck and you can resume from that point with one click, so it doesn't waste money redoing the work that already succeeded.

### Step 1: Watching your sources

The app polls your sources continuously in the background, on whatever interval you set per source. Different sites are checked in parallel; sources that share the same host are checked one at a time with a small delay so you don't get rate-limited (this matters for community-run services like Nitter). If a source keeps failing, the app slows down its polling automatically and eventually disables it if the failures look permanent (a 404 or a dead DNS, for example). New posts are deduplicated across your sources so an X account and its Nitter mirror don't both add the same content.

```mermaid
flowchart LR
    S1["RSS feed"] --> P["Background poller"]
    S2["Website"] --> P
    S3["X account"] --> P
    S4["Nitter mirror"] --> P
    P --> H["Drop duplicates<br/>(same post from<br/>multiple sources)"]
    H --> POSTS[("Collected posts")]
    P -. "if a source keeps failing" .-> BO["Slow down,<br/>eventually disable"]
```

### Step 2: Picking what's worth covering

When it's time to generate an episode, the app reads the unprocessed posts and turns them into articles. Long-form posts (news articles, blog posts) become one article each. Short-form posts (tweets) are grouped by author and then by conversation: a tweet plus its replies become a single article, with the original tweet's URL and title. Grouping by author first matters when one source is a combined feed carrying many accounts (a Narro feed, say) rather than a single account, so one person's reply chain is never spliced onto someone else's post. Then a fast, cheap language model reads every article and gives it a relevance score from 0 to 10, a short summary, and (if you configured subtopics) tags it with the subtopic it belongs to. Anything below your relevance threshold is dropped. Scoring is the longest part of generation when there's a big backlog, so the dashboard shows it live (for example "Scoring 142 / 318") on the generating episode row.

Most of that work happens before generation starts: when polling a source finishes, its new articles are scored right away rather than waiting for the scheduled run, so the backlog is usually already ranked by the time an episode is due. Eager scoring respects the same per-podcast cost gate as the rest of the pipeline. If the last poll round is nevertheless too old when a scheduled generation begins, a catch-up poll runs first so the episode isn't built from stale sources.

```mermaid
flowchart LR
    POSTS[("Collected posts")] --> AGG{"Post type?"}
    AGG -->|"news / blog"| ART1["One post,<br/>one article"]
    AGG -->|"tweets"| TH["Group tweet<br/>+ its replies"]
    TH --> ART2["Conversation<br/>article"]
    ART1 --> SCORE["Read it,<br/>score it,<br/>summarize it"]
    ART2 --> SCORE
    SCORE -->|"not interesting"| DROP["Dropped"]
    SCORE -->|"keeper"| READY["Ready for the script"]
```

### Step 3: Writing the script

A second, smarter language model writes the actual episode. Before it starts, the app groups today's articles by topic and compares them against recent episodes: brand-new topics go in fresh, topics that follow up on something covered earlier get a "follow-up" hint so the script can naturally reference previous coverage, and topics you already covered to death get skipped. While writing, the model can search a full-text index of all your past episodes (so it knows what's already been said weeks ago) and can optionally do real web searches via Tavily for extra context on big stories. The opening, transitions, sign-off, and other patterns get rotated automatically so episodes don't all sound the same.

A few guards keep this stage predictable: the number of articles fed into a single compose request is capped to the highest-relevance ones so a big backlog can't blow past the model's context, both the compose request and the topic-grouping call ahead of it bound their output tokens (the grouping budget scales with how many articles it has to sort, and a response the model truncates is salvaged rather than thrown away), each stage has a timeout sized to what that stage actually takes rather than one value shared across the pipeline, and for dialogue and interview styles the speaker tags the model emits are checked against the podcast's configured roles. An invalid tag (a leaked tool-call artifact, say) is re-prompted with a bounded number of retries and, if it persists, fails at the compose stage rather than surfacing later as a missing voice during TTS.

```mermaid
flowchart TD
    READY["Today's articles"] --> DEDUP["Group by topic,<br/>compare to recent episodes"]
    HIST[("Past episodes<br/>(searchable)")] --> DEDUP
    DEDUP -->|"new / follow-up / skip"| COMP["Script writer"]
    SUBT["Your subtopic weights<br/>(more time on what matters,<br/>capped rapid-fire for the rest)"] --> COMP
    ROT["Rotated openings,<br/>transitions, sign-offs"] --> COMP
    T1["Search past episodes<br/>(avoid repeating)"] --> COMP
    T2["Web search<br/>(extra context, optional)"] --> COMP
    COMP --> SCRIPT["Episode script"]
    SCRIPT --> RECAP["Short recap<br/>+ show notes"]
    SCRIPT --> SRC["Sources page,<br/>grouped by topic"]
```

If you turned on "require review", the pipeline pauses here so you can read, edit, or discard the script before any audio is recorded.

### Step 4: Recording the audio

The finished script is sent to a text-to-speech provider of your choice (OpenAI, ElevenLabs, or Inworld). Before sending, the app cleans the script up for TTS: it strips out em-dashes and en-dashes (which TTS models tend to read out loud as "dash"), and it injects pronunciation hints if you've set up a pronunciation dictionary for the podcast. Long scripts are split into chunks at the most natural boundary available (a paragraph break first, then a line break, then a sentence end, then a word gap) so the TTS model doesn't choke and the splices land where a speaker would already pause, then the resulting audio chunks are stitched back together into a single MP3. A short silence is prepended so players don't clip the first word, encoded to match the sample rate, channel count, and bitrate of the speech chunks (providers differ: Inworld returns 48kHz audio, ElevenLabs 44.1kHz). Stitching is a stream copy, so a file whose format changed partway through would be rejected by some podcast platforms. Inworld follows the provider's own generating-speech guidance: every chunk is sent along with the text of the chunks before it, so intonation carries across a splice instead of resetting; free-form delivery directions the script writer emits (`[warm and conversational with an easy pace]`) are re-emitted at the head of each following chunk so a direction isn't lost when a turn is split, and are stripped on models that would read them aloud; non-verbal tags are spelled exactly as Inworld documents them; and the podcast's language is sent explicitly rather than left to auto-detection. It defaults to the `inworld-tts-2` model, exposes Inworld's `STABLE` / `BALANCED` / `CREATIVE` delivery modes per podcast, and offers an **Enhanced Audio Quality** setting, which applies denoising to reduce background noise and artifacts. ElevenLabs and Inworld support multiple voices for dialogue and interview styles; the speaker tags in those scripts are parsed tolerantly, so an occasional malformed tag from the script writer never silently drops a spoken turn. Transient hiccups during generation (a rate limit, a dropped connection, a timeout) are retried automatically per chunk with exponential backoff, so a single flaky request doesn't fail the whole episode.

```mermaid
flowchart LR
    SCRIPT["Script"] --> SAN["Clean up for TTS<br/>(remove dashes,<br/>add pronunciations)"]
    SAN --> CHUNK["Split into chunks<br/>at sentence boundaries"]
    CHUNK --> TTS{"Your TTS<br/>provider"}
    TTS -->|"OpenAI"| O["Single voice"]
    TTS -->|"ElevenLabs"| EL["Single or<br/>multi-voice"]
    TTS -->|"Inworld"| IW["Expressive,<br/>multi-voice"]
    O --> FF["Stitch chunks<br/>into one MP3"]
    EL --> FF
    IW --> FF
    FF --> MP3["Finished episode"]
```

### Step 5: Publishing, and recovering from failures

The MP3, recap, and show notes become an episode in your podcast's RSS feed. The feed is available two ways: a live HTTP endpoint, and a static `feed.xml` file written to disk so you can host the whole podcast on a static file server, S3, or a CDN. From the dashboard you can also publish individual episodes to FTP or SoundCloud. If anything in steps 2-4 fails partway through (a flaky API, a hit cost limit, a TTS timeout), the app remembers which stage failed and keeps all the work it had already done. A single "Retry" click resumes from that exact stage, so the LLM calls you already paid for aren't repeated. Regenerating an episode is different from retrying one: it recomposes from the articles an episode already chose, so an episode that failed before it got that far is rejected up front with a clear message rather than producing another failed episode.

```mermaid
flowchart LR
    MP3["Finished episode"] --> RSS["RSS feed<br/>(podcast apps subscribe)"]
    MP3 --> FTP["FTP upload<br/>(your own server)"]
    MP3 --> SC["SoundCloud<br/>(auto playlist per podcast)"]

    F1["Stage failed?"] -. "click Retry" .-> RESUME["Resume from<br/>where it stopped"]
```

Each user can create multiple podcasts, each with its own sources, topic, language, models, TTS provider/voices, style, and generation schedule. See [docs/configuration.md](docs/configuration.md) for every setting.

## Architecture

A small Spring Boot backend handles everything (polling sources, running the LLM pipeline, generating audio, publishing). A Next.js dashboard talks to it over HTTP. SQLite holds all state on disk. External providers (LLM, TTS, web research, publication targets) are called from the backend only.

Background work runs on Kotlin coroutines rather than thread pools: coroutine roots never block, blocking I/O (HTTP, database, file, TTS) is confined to `Dispatchers.IO`, transactional work stays on one dispatcher, and provider and publisher abstractions are `suspend` functions. Manually triggered generation follows the same rule: the request starts the run in the background and returns immediately, reporting a conflict if that podcast is already generating.

```mermaid
flowchart LR
    USER(("You")) -->|browse, edit,<br/>approve, listen| FE["Next.js Dashboard<br/>(frontend/)"]
    FE -->|HTTP /api/*| BE["Spring Boot Backend<br/>(localhost:8085)"]
    BE --> DB[("SQLite<br/>./data/*.db")]
    BE --> FS[("Audio + feed.xml<br/>./data/episodes/")]
    BE -.->|optional| EXT["External APIs<br/>OpenRouter, OpenAI, ElevenLabs,<br/>Inworld, Tavily, FTP, SoundCloud, X"]
```

## Prerequisites

- Java 24+ (Java 25+ requires `--enable-native-access=ALL-UNNAMED` for the SQLite JDBC driver, `start.sh` and `mvnw spring-boot:run` handle this automatically)
- FFmpeg (for audio concatenation and duration detection)
- An LLM provider, one of:
  - [OpenRouter](https://openrouter.ai/) API key (cloud, multiple models)
  - [Ollama](https://ollama.com/) running locally (free, no API key needed)
- A TTS provider, one of:
  - [OpenAI](https://platform.openai.com/) API key (default)
  - [ElevenLabs](https://elevenlabs.io/) API key (for advanced voices and multi-speaker dialogue)
  - [Inworld AI](https://inworld.ai/tts) API key (for expressive voices with rich markup support)

## Setup

1. Install [direnv](https://direnv.net/) and hook it into your shell (e.g. `eval "$(direnv hook zsh)"` in `~/.zshrc`).

2. Create a `.envrc` file in the project root:

   ```bash
   export APP_ENCRYPTION_MASTER_KEY=<base64-encoded 256-bit AES key>
   ```

3. Allow the file: `direnv allow`

Generate an encryption key: `openssl rand -base64 32`

`APP_ENCRYPTION_MASTER_KEY` is the only required environment variable. It is used to encrypt API keys stored in the database.

All other credentials (LLM providers, TTS providers, publishing targets) are managed per-user via the web dashboard or the Provider Configuration API (see [docs/api-reference.md](docs/api-reference.md#provider-configuration)). Optionally, you can set environment variables as global fallbacks for users who haven't configured their own keys:

| Variable | Purpose |
|----------|---------|
| `OPENROUTER_API_KEY` | Global fallback for OpenRouter LLM provider |
| `OPENAI_API_KEY` | Global fallback for OpenAI TTS provider |
| `ELEVENLABS_API_KEY` | Global fallback for ElevenLabs TTS provider |
| `INWORLD_AI_JWT_KEY` / `INWORLD_AI_JWT_SECRET` | Global fallback for Inworld AI TTS provider (combined as `key:secret` for Basic auth) |
| `TAVILY_API_KEY` | Global fallback for the Tavily web-search provider used by the deep-dive research tool (see [docs/deep-dive-research.md](docs/deep-dive-research.md)) |
| `APP_SOUNDCLOUD_CLIENT_ID` / `APP_SOUNDCLOUD_CLIENT_SECRET` | SoundCloud OAuth app credentials (see [docs/publishing.md](docs/publishing.md#publishing-to-soundcloud)) |
| `APP_X_CLIENT_ID` / `APP_X_CLIENT_SECRET` | X (Twitter) OAuth app credentials (see [docs/publishing.md](docs/publishing.md#monitoring-x-twitter-accounts)) |

> **Without direnv?** You can alternatively export the variables in your shell profile (e.g. `~/.zshenv`) or source a `.env` file manually before running the app.

### Provider Configuration

LLM, TTS, and research providers are configured per-user via the **web dashboard** (Settings > API Keys) or the Provider Configuration API. Supported providers:

- **LLM**: `openrouter` (default), `openai`, `ollama`
- **TTS**: `openai` (default), `elevenlabs`, `inworld`
- **Research** (optional, for the deep-dive web-search tool): `tavily`

**Using Ollama (local, free):** Start [Ollama](https://ollama.com/) locally, pull a model (`ollama pull llama3`), then configure it as your LLM provider in the dashboard or via the API. No API key needed, uses `http://localhost:11434/v1` by default.

### Starting the Application

```bash
./start.sh        # runs in background, logs to app.log
./stop.sh         # graceful stop with 10s timeout
```

Or run directly (environment variables are loaded automatically by direnv):

```bash
./mvnw spring-boot:run
```

The app starts on `http://localhost:8085`. Data is stored in `./data/` (SQLite DB + episode audio files).

### Web Dashboard

A Next.js dashboard is available in `frontend/` for visual management of podcasts, episodes, and publications.

```bash
cd frontend && npm run dev
```

The dashboard provides:
- **User settings**, gear icon in the header opens a settings page to edit your profile name and manage API keys (LLM and TTS provider configs) with a wizard-style dialog. All API keys are stored encrypted
- **Podcast overview**, browse all podcasts with style badges, topics, and quick-access settings gear icon
- **Podcast settings**, edit all podcast configuration (general, LLM, TTS, content, publishing) via a tabbed settings page with provider/model dropdowns for LLM and TTS selection. The podcast detail page also has a danger zone for deleting the podcast, which cascades to its episodes, sources, and audio and requires typing the podcast name to confirm
- **Episode management**, view episodes with server-side pagination (10/20/50/100 per page, default 20) and multi-select status filtering; approve/discard/regenerate pending reviews; regenerate audio on generated episodes; retry failed episodes from the stage that failed; play the MP3 inline from the table. Click any episode row to open the detail page. Shows the generation schedule in human-readable form, in the podcast's timezone
- **Episode detail page**, dedicated page per episode with tabs for Script (chat-bubble rendering), Articles (grouped by source with relevance scores and collapsible sections; an article aggregated from several posts shows its thread size and expands to the individual posts), Publications, and **Costs** (per-stage breakdown: scoring, dedup, compose, recap, TTS, research, plus total). Shows episode metadata, recap, inline audio player, and contextual action buttons (Approve, Discard, Publish, Regenerate, Regenerate Audio, Retry, Regenerate Recap)
- **Upcoming episode preview**, see collected articles for the next episode (with the same thread expansion as the episode Articles tab), preview the script via Server-Sent Events with live progress through every pipeline stage (aggregating, scoring, deduplicating, composing), and trigger episode generation on demand. Shows next scheduled generation time
- **Source export**, download all configured sources as a markdown file from the Sources tab
- **Publish wizard**, publish generated episodes to FTP or SoundCloud via a step-by-step wizard. SoundCloud upload quota is freed automatically server-side (oldest podcast tracks are deleted just enough to fit, then the upload retries), and OAuth expiry surfaces a re-authorize action
- **Publications tab**, view all publications across the podcast in one paginated table (newest first) with track/playlist links and republish/unpublish actions

The frontend proxies API calls to `http://localhost:8085` via Next.js rewrites.

## Customizing Your Podcast

Each podcast is configurable end to end: the topic and language, the style (news-briefing, casual, deep-dive, executive-summary, dialogue, interview), the LLM models per pipeline stage, the TTS provider/voices/settings, the schedule (cron + timezone), subtopic weights, custom prompt instructions, a sponsor message, a pronunciation dictionary, cost limits, and more. Per-podcast `requireReview` lets you preview and edit the script before any audio is generated.

See [docs/configuration.md](docs/configuration.md) for the full table of settings, the briefing styles, TTS provider details, model registry, and the cost gate.

### Episode Review

When `requireReview` is enabled on a podcast, the generation pipeline pauses after the LLM produces a script (no audio is generated yet). This lets you review, edit, or discard the script before committing to TTS costs.

The episode workflow is: `PENDING_REVIEW` → (edit script if needed) → `APPROVED` → `GENERATING_AUDIO` (TTS in progress) → `GENERATED`. The `GENERATING_AUDIO` status is persisted in the database so the UI shows a "Generating audio..." spinner across page reloads; on app startup any stale `GENERATING_AUDIO` episodes are recovered as `FAILED`. You can also discard an episode (discarding resets non-aggregated articles so they are included in the next generation run, while aggregated articles from X/Nitter sources are deleted so their posts get re-aggregated fresh with any new posts on the next run). Articles linked to published episodes are never reset or deleted during discard, preventing published content from being reprocessed.

Episodes can be **regenerated** (re-composes the script from the same articles using the current podcast settings, creating a new episode). Regeneration is available for `PENDING_REVIEW` and `DISCARDED` episodes, and is blocked if any episode on the same day has already been published.

Episodes can also be **audio-regenerated** without recomposing the script: a separate `regenerate-audio` action reruns TTS on the existing script (useful after changing the TTS model, voice, `deliveryMode`, or enhanced audio quality) and overwrites the previous MP3. The episode's audio can be played inline from the dashboard via a streaming `audio` endpoint.

If the pipeline fails mid-run, the `pipelineStage` is preserved on the episode along with all intermediate state (scored articles, dedup links, script). A **retry** action resumes from exactly the failed stage without re-running earlier LLM work.

If recap generation produced an empty or low-quality recap, a **regenerate-recap** action recomputes the recap and show-notes from the existing script and re-exports the static feed.

### Cost Tracking

Episode responses include token usage and costs broken down per pipeline stage: **Scoring**, **Dedup**, **Compose**, **Recap**, **TTS**, and **Research**, plus the number of TTS synthesis calls an episode made. LLM cost comes from the provider's own reported charge wherever one is available (OpenRouter returns the exact cost of every call, and a cached call replays the cost of the original), falling back to the per-model rates configured in `application.yaml` for calls that report nothing. Each episode records where its cost came from, so an actual charge is distinguishable from an estimate, including the mixed case where only some stages reported one. Stage costs are tracked with sub-cent precision, so a stage that costs a fraction of a cent is not rounded away to zero. The dashboard renders this breakdown in a dedicated **Costs** tab on the episode detail page. Pricing is configured per model in `application.yaml`; see [docs/configuration.md#model-configuration](docs/configuration.md#model-configuration). Before any LLM call, a **cost gate** estimates the total spend and skips the run if it would exceed a configurable threshold (`maxLlmCostCents` per podcast, or the global `app.llm.max-cost-cents`).

## Deep-Dive Web Research

When `deepDiveEnabled` is set on a podcast, the script composer is given a `webSearch` tool backed by [Tavily](https://tavily.com) and may call it (up to 3 times per episode) to fetch outside context for the most newsworthy stories. See [docs/deep-dive-research.md](docs/deep-dive-research.md) for configuration and API key resolution.

## Publishing

Episodes can be published to multiple targets after generation: **FTP** and **SoundCloud** are supported, configured per-podcast, with per-target publication status tracking. Publishing can be automatic: with auto-publish enabled on a target, an episode goes out to that target as soon as generation completes, and a target that fails does not interrupt generation. An optional per-podcast approval gate can require an episode to be explicitly approved for publication first, in which case publishers refuse it until then. The dashboard's publish wizard handles OAuth and re-auth; SoundCloud upload quota is freed automatically server-side when full. FTP(S) connections encrypt the data channel as well as the control channel, tolerate servers behind NAT, and treat the configured passive/active transfer mode as a preference: the data channel is verified before any file moves, and the other mode is used automatically if the configured one cannot open it. Connection failures name the phase that failed, so a network blocking the FTP port is distinguishable from bad credentials. See [docs/publishing.md](docs/publishing.md) for FTP setup, SoundCloud OAuth, X (Twitter) OAuth for sources, and using Nitter as a free alternative.

## Database Backups

The application can take scheduled, compressed snapshots of its SQLite database. Each backup is produced with SQLite `VACUUM INTO` (a transactionally consistent, compact copy of schema + data, safe to take while the app is running) and gzip-compressed to `data/backups/ai-summary-podcast-<yyyyMMdd-HHmmss>.db.gz`. Older backups beyond the configured retention count are pruned automatically.

The schedule is editable at runtime from the **Settings → Backups** tab (no restart needed): toggle backups on/off, set the cron expression (UTC, with a human-readable preview and next-run time), and set how many backups to keep. A **Back up now** button triggers an immediate backup, and the tab lists existing backups with size and timestamp. Initial defaults come from `app.backup.*` in `application.yaml` (`enabled`, `cron`, `directory`, `retention-count`); the persisted settings are the runtime source of truth.

The admin API lives under `/admin/backup`: `GET/PUT /admin/backup/settings`, `POST /admin/backup` (run now), and `GET /admin/backup` (list).

**Restore:** stop the app, decompress a backup (`gunzip -c data/backups/ai-summary-podcast-<timestamp>.db.gz > data/ai-summary-podcast.db`), remove any stale `-wal`/`-shm` sidecar files, and start the app.

## API

All resources are exposed over HTTP under `/users/{userId}/...`. See [docs/api-reference.md](docs/api-reference.md) for the full endpoint list (users, podcasts, episodes, sources, publishing, OAuth callbacks, SSE events, voices, provider configuration) plus an example podcast-creation payload.

## Running Tests

```bash
./mvnw test
```

Tests use [MockK](https://mockk.io/) for mocking.
