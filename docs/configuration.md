# Configuration Reference

This document covers every per-podcast setting, the briefing styles, and how to configure TTS providers and the LLM model registry. Most of these knobs are exposed in the dashboard's podcast settings page, but you can also set them via the API.

## Per-podcast settings

| Setting | Default | Description |
|---------|---------|-------------|
| `name` | — | Display name shown in your podcast app |
| `topic` | — | Interest area used by the LLM to filter relevant articles |
| `language` | `"en"` | Language for the briefing script, date formatting, and RSS feed metadata (actual audio language support depends on TTS provider) |
| `style` | `"news-briefing"` | Briefing tone, see [Briefing styles](#briefing-styles) |
| `ttsProvider` | `"openai"` | TTS provider (`openai`, `elevenlabs`, or `inworld`) |
| `ttsVoices` | `{"default": "nova"}` | Voice configuration, see [TTS configuration](#tts-configuration) |
| `speakerNames` | `null` | Display names for speakers, e.g. `{"interviewer": "Alice", "expert": "Bob"}`. Used in dialogue/interview scripts so speakers address each other by name |
| `ttsSettings` | — | Provider-specific settings (e.g. `{"speed": "1.25"}` for OpenAI, `{"stability": "0.5"}` for ElevenLabs, `{"model": "inworld-tts-1.5-max", "speed": "1.0", "temperature": "1.1"}` for Inworld) |
| `llmModels` | — | Override LLM models per stage with `{provider, model}` objects, see [Model configuration](#model-configuration) |
| `targetWords` | `1500` | Approximate word count for the briefing script |
| `cron` | `"0 0 6 * * *"` | Generation schedule in cron format (default: daily at 6 AM). Evaluated in the podcast's `timezone` |
| `timezone` | `"UTC"` | IANA timezone identifier (e.g. `Europe/Amsterdam`) used to interpret `cron`. DST transitions are handled automatically via `java.time.ZoneId`. If the app is offline when a trigger fires, the scheduler catches up on the next startup as long as the missed trigger is still on the same calendar day in the podcast's timezone |
| `customInstructions` | — | Free-form instructions appended to the LLM prompt (e.g. "Focus on recent breakthroughs" or "Avoid financial topics") |
| `relevanceThreshold` | `5` | Minimum relevance score (0-10) for an article to be included in the briefing |
| `subtopics` | `null` | Optional map of subtopic name to importance weight (1-10) within the podcast's topic (e.g. `{"LLM releases": 10, "Dev tools": 5, "AI ethics": 2}`). The Stage 1 LLM call classifies each article into one of the listed subtopics. High-weight subtopics get full script segments with word budgets proportional to weight; low-weight subtopics (at or below `rapidFireWeightThreshold`) are rolled into a labeled "And in brief" rapid-fire segment at the end. Empty/unset disables the feature and keeps the legacy flat layout |
| `rapidFireWeightThreshold` | `3` | Weight cutoff for the rapid-fire tier (0-10). Subtopics with weight at or below this value (plus unclassified articles in a synthetic "Other" bucket of weight 1) are rolled into a single rapid-fire segment. `0` means nothing is rapid-fire, `10` means everything is rapid-fire |
| `rapidFireMaxItems` | `null` (uses system default `6`) | Caps how many articles the rapid-fire segment covers (0-50). Articles past the cap are ranked by `(bucket weight desc, relevance score desc)` and dropped from the episode entirely. Keeps the closing segment readable instead of cramming 15+ items into a few sentences. `0` disables the rapid-fire tier entirely |
| `fullBodyThreshold` | `5` | When the number of relevant articles is below this threshold, the composer uses full article bodies instead of summaries for richer content |
| `requireReview` | `false` | When `true`, generated scripts pause for review before TTS |
| `maxLlmCostCents` | `null` | Per-podcast LLM cost threshold in cents, see [Cost gate](#cost-gate) |
| `maxArticleAgeDays` | `null` | Maximum age of articles to include (default: 7 days). Articles older than this are skipped during ingestion |
| `sponsor` | `null` | Sponsor configuration, e.g. `{"name": "Acme Corp", "message": "building the future"}`. Adds a sponsor message after the introduction and in the sign-off |
| `pronunciations` | `null` | IPA pronunciation dictionary, maps terms to phonemes (e.g. `{"Anthropic": "/ænˈθɹɒpɪk/"}`) for correct TTS pronunciation. Currently supported by Inworld TTS |
| `recapLookbackEpisodes` | `null` | Number of recent episodes to check for topic overlap (default: 7). The dedup filter uses article titles and summaries from these episodes to prevent repeating previously covered topics |
| `deepDiveEnabled` | `false` | When `true`, the composer is given a `webSearch` tool backed by Tavily, see [deep-dive-research.md](deep-dive-research.md) |
| `composeSettings` | — | Composer (script LLM) settings. Supports `temperature` (range `0.0`-`2.0`, default `0.95`), which controls sampling variety in `BriefingComposer`, `DialogueComposer`, and `InterviewComposer`, and `reasoningEffort` (`none`, `minimal`, `low`, `medium`, `high`, `xhigh`, `max`; default from `app.compose.reasoning-effort`, itself `medium`), which sets how much the model plans before writing. Reasoning tokens are billed as output tokens, so this is the largest single cost lever in the pipeline: turning it down makes composition faster and cheaper. The composer also rotates six prompt axes (opening style, transition vocabulary, sign-off shape, teaser shape, topic-entry pattern, penultimate-exchange shape) deterministically per `(podcastId, episodeDate)` to reduce structural repetition across episodes |

## Briefing styles

| Style | Tone |
|-------|------|
| `news-briefing` | Professional news anchor: structured, authoritative, smooth transitions |
| `casual` | Friendly podcast host: conversational, relaxed, like talking to a friend |
| `deep-dive` | Analytical exploration: in-depth analysis and thoughtful commentary |
| `executive-summary` | Concise and fact-focused: minimal commentary, straight to the point |
| `dialogue` | Multi-speaker conversation: requires ElevenLabs or Inworld TTS and at least two voice roles |
| `interview` | Interviewer/expert conversation: asymmetric roles (~35% interviewer, ~65% expert). Features "coming up" topic teasers (5+ articles), strategic cliffhangers, spontaneous interruptions (excited, skeptical, confused, connecting dots, disagreement), and strict 3-4 sentence expert turn limits. Requires ElevenLabs or Inworld TTS with exactly `interviewer` and `expert` voice roles |

## TTS configuration

Three TTS providers are supported: **OpenAI** (default), **ElevenLabs**, and **Inworld AI**. Configure your preferred provider and API key via the web dashboard (Settings > API Keys) or the Provider Configuration API (see [api-reference.md](api-reference.md#provider-configuration)).

**OpenAI**: Voices: `alloy`, `echo`, `fable`, `nova`, `onyx`, `shimmer`. Settings: `{"speed": "1.25"}`.

**ElevenLabs**: Supports single-voice monologue, multi-speaker dialogue, and interview styles. Use `GET /users/{userId}/voices?provider=elevenlabs` to discover available voice IDs.

**Inworld AI**: Requires JWT key and secret as `key:secret`. Supports monologue, dialogue, and interview styles with rich expressiveness markup (emphasis, non-verbal cues, IPA phonemes). Scripts are automatically post-processed to sanitize LLM output for Inworld (em-dashes and en-dashes are stripped before TTS to prevent the model from vocalising them). A per-podcast pronunciation dictionary (`pronunciations` field) can map terms to IPA phonemes, which the LLM is instructed to apply on every occurrence of a term (not just the first). Models: `inworld-tts-1.5-max` (default), `inworld-tts-1.5-mini`, `inworld-tts-2`. Settings: `{"model": "inworld-tts-1.5-max", "speed": "1.0", "temperature": "0.8"}`. For `inworld-tts-2`, set `{"model": "inworld-tts-2", "deliveryMode": "STABLE" | "BALANCED" | "EXPRESSIVE"}` instead of `temperature` (the Inworld API treats them as mutually exclusive on TTS-2). Use `GET /users/{userId}/voices?provider=inworld` to discover available voice IDs.

Voice configuration uses the `ttsVoices` map:
- Monologue: `{"default": "nova"}` (or any ElevenLabs voice ID)
- Dialogue: `{"host": "<voice_id>", "cohost": "<voice_id>"}`: the key names become the speaker tags in the generated script
- Interview: `{"interviewer": "<voice_id>", "expert": "<voice_id>"}`: fixed role keys required for the interview style

## Model configuration

All model definitions (LLM and TTS) live under `app.models` in `application.yaml`, organized by provider. Each model has a `type` (`llm` or `tts`), optional cost fields, and an optional `selectable` flag (default `true`):

```yaml
app:
  models:
    openrouter:
      "[deepseek/deepseek-v4-flash-0731]":
        type: llm
        input-cost-per-mtok: 0.05
        output-cost-per-mtok: 0.16
      "[z-ai/glm-5.3]":
        type: llm
        input-cost-per-mtok: 1.12
        output-cost-per-mtok: 3.52
      "[anthropic/claude-opus-5]":
        type: llm
        input-cost-per-mtok: 5.00
        output-cost-per-mtok: 25.00
        selectable: false
    openai:
      "[tts-1-hd]":
        type: tts
        cost-per-million-chars: 15.00
    inworld:
      "[inworld-tts-1.5-max]":
        type: tts
        cost-per-million-chars: 10.00
  llm:
    defaults:
      filter:
        provider: openrouter
        model: deepseek/deepseek-v4-flash-0731
      dedup:
        provider: openrouter
        model: deepseek/deepseek-v4-flash-0731
      compose:
        provider: openrouter
        model: z-ai/glm-5.3
```

Model name keys containing `/`, `-`, or `.` must be quoted with `"[...]"` for Spring Boot's relaxed property binding. A key may be a provider routing alias rather than a pinned release, in which case it must be the exact slug the provider accepts, including OpenRouter's leading `~`. Alias pricing is approximate, since the alias resolves to whichever release is current, and a new release can bring different reasoning defaults with it, so a pinned release is preferred for a stage default.

For an OpenRouter model, the configured price should be that of the cheapest endpoint clearing the quantization floor described below, since that is the price routing can actually select.

### Models that cannot be selected

Requests to OpenRouter state the endpoint quantizations they accept, which excludes any endpoint reporting `unknown`. Vendor-native endpoints do not disclose a quantization, so every model served only by its own vendor (OpenAI, Anthropic, Google) answers with `404 No endpoints found`. Such a model is marked `selectable: false`: its pricing stays, because a registry entry is how a past episode's cost is resolved and deleting one would leave those episodes with no cost at all, but it is withheld from the models offered for selection so a stage cannot be pointed at something that cannot serve a request. This limits stage models to open-weight models served by third-party inference providers.

Per-podcast overrides use the `llmModels` field, mapping stage names (`filter`, `dedup`, `compose`) to `{provider, model}` objects:

```json
{
  "llmModels": {
    "compose": {"provider": "openrouter", "model": "deepseek/deepseek-v4-pro"}
  }
}
```

The `GET /config/defaults` endpoint returns the selectable models grouped by provider and type, used by the frontend to populate model selection dropdowns. Provider keys are retained even when every model under them is withheld. The endpoint does not validate an override sent directly to the podcast API, so an unselectable model set that way fails at generation time rather than on save.

### The dedup gate

The dedup stage runs a Jev decision ahead of its clustering call, dropping the candidates recent episodes already covered. It is configured globally under `app.llm.dedup.gate` rather than per podcast, because it takes one closed decision that does not vary by show:

```yaml
app:
  llm:
    dedup:
      gate:
        enabled: true
        url: https://openrouter.ai/api/alpha/decisions
        model: typesafe/jev-1.13
        threshold: 0.8
        summary-max-chars: 300
        max-request-chars: 150000
        timeout: 30s
```

Jev is not a chat model and is absent from OpenRouter's `/api/v1/models` listing, so it is not a stage model and cannot be set through `llmModels`. Its `url` is configured rather than derived from the stored OpenRouter base URL, which points at the chat API. The credential is the existing OpenRouter one: without it the gate asks nothing and the stage runs ungated.

`threshold` is the probability at or above which a candidate counts as already covered. It errs high on purpose, because a false exclusion silently removes a story from the episode while a false inclusion only leaves the clustering call the work it already does. `summary-max-chars` and `max-request-chars` bound the request: the endpoint rejects on size rather than on question count, and every measurement behind these defaults is recorded in `knowledge/references/jev-decisions-endpoint.md`.

Setting `enabled: false` restores the stage's behaviour from before the gate existed, which is also what happens by itself whenever the endpoint is unavailable.

## Cost gate

Before making any LLM API calls, the pipeline estimates the total cost (scoring + dedup filter + composition) and compares it against a configurable threshold. If the estimated cost exceeds the threshold, the entire pipeline run is skipped and a warning is logged.

The global default threshold is configured in `application.yaml`:

```yaml
app:
  llm:
    max-cost-cents: 200    # $2.00, skip pipeline if estimated cost exceeds this
```

Each podcast can override the global threshold via `maxLlmCostCents`. When set to `null` (the default), the global value applies. The estimation is pessimistic (it assumes all articles pass relevance filtering) so actual costs will typically be lower than estimated. If model pricing is not configured, the cost gate is bypassed with a warning.

## Static feed export

After each feed-changing event (episode generation, approval, or cleanup), the system writes a `feed.xml` file to the podcast's episode directory (`data/episodes/{podcastId}/feed.xml`). This lets you host the entire directory on a static file server (S3, Nginx, GitHub Pages) without running the application.

To use a different base URL for the static feed's enclosure links (e.g., your CDN), set:

```yaml
app:
  feed:
    static-base-url: https://cdn.example.com
```

When not set, the static feed uses the same `app.feed.base-url` as the dynamic endpoint. The dynamic HTTP feed at `/users/{userId}/podcasts/{podcastId}/feed.xml` remains available regardless.
