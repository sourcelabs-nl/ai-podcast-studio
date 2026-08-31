## Why

Episode 191 (31 August 2026) failed 24 seconds after it started, in the topic-dedup stage, on all three attempts:

```
UnexpectedEndOfInputException: Unexpected end-of-input in property name
(through reference chain: DedupResult["clusters"]->java.util.ArrayList[234])
```

The dedup response was cut off mid-JSON at cluster 234, so `BeanOutputConverter` could not parse any of it, and the whole episode was lost.

The cut came from our own ceiling, not the provider's. `DEDUP_MAX_OUTPUT_TOKENS = 8000` is the only output cap on that call (the dedup model has a 1M context window), and its comment claims 8000 is "well above any real output". Historical runs show that is false:

| candidates | clusters returned |
|---|---|
| 37 | 29 |
| 39 | 39 |
| 56 | 50 |
| 68 | 44 |
| 174 | 21 |

Cluster count frequently tracks candidate count almost 1:1, because the prompt requires that "every candidate article must appear in exactly one cluster". Response size therefore scales linearly with the candidate count, and at roughly 90 output tokens per cluster a 183-candidate day needs some 16,000 tokens. A fixed 8000 was always going to truncate on a busy day; 31 August was simply the first day with 183 eligible candidates.

Two things made the failure worse than it had to be:

- A truncated response was discarded whole. The 234 complete clusters in it were perfectly usable, and dedup is the one stage whose partial output is still safe to act on, because an article no surviving cluster mentions is simply not composed.
- Regenerating the failed episode could never work. `regenerateEpisodeAsync` creates the GENERATING episode before checking that the source episode has linked articles, and episode 191 failed before article selection, so it had none. Two retries produced `IllegalStateException: No articles found for episode 191` and left two junk FAILED episodes (192, 193) behind.

## What Changes

- The dedup output budget scales with the candidate count: `90 * candidates.size`, clamped to `[8000, 32000]`. A legitimate large response now fits, while the ceiling still cuts a repetition loop off in seconds rather than letting it stream for minutes. 32000 is far inside the dedup model's 1M context window.
- A truncated dedup response is salvaged rather than discarded. The strict parse is tried first; on failure the complete cluster objects are recovered from the truncated `clusters` array with a streaming parse. A salvage is accepted only when it still selects at least `app.compose.max-articles` (40) articles, the point at which the lost tail provably could not have changed what gets composed, since the compose cap would have dropped the surplus anyway. Below that the attempt fails and the existing retry applies. Every salvage is logged at WARN.
- Dedup failure remains fatal to the episode. When no attempt yields a strict parse or an acceptable salvage, the error still propagates and the episode still fails. The system continues never to compose un-deduped articles.
- Regeneration validates before it creates anything. `regenerateEpisodeAsync` checks for linked articles first and throws the new `EpisodeNotRegenerableException`, mapped to HTTP 409 by a new `PodcastExceptionHandler`. No GENERATING episode is created, so a regenerate that cannot work no longer leaves a junk FAILED episode.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `article-dedup-filter`: the output cap becomes a candidate-scaled budget, and the failure requirement gains truncation salvage ahead of the existing propagate-and-fail behaviour.
- `episode-regeneration`: an episode with no linked articles is rejected with 409 before a new episode is created, replacing the old 500-after-creating-a-failed-episode behaviour.

## Impact

- Backend: `TopicDedupFilter` (candidate-scaled budget, raw-content parse with streaming salvage, `AppProperties` injected for the compose cap); `PodcastService.regenerateEpisodeAsync` (validate before create); new `PodcastExceptions.kt` and `PodcastExceptionHandler.kt`.
- Tests: `TopicDedupFilterTest` gains budget and salvage cases; `PodcastServiceTest` gains the regenerate guard.
- No schema, frontend, or configuration change. The budget constants are code-level, consistent with the fixed cap they replace.
- Episodes 192 and 193 are junk rows from the failed regenerate attempts and can be discarded; episode 191 needs a fresh generation, not a regenerate, because it never selected any articles.
