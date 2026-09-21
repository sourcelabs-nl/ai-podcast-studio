## Why

Episode 202 failed three times on the dedup stage with `Truncated dedup response salvaged only 0 selectable article(s) from 0 cluster(s), below the 40 needed to compose`. The response was not truncated. It was complete, valid JSON that arrived in the wrong wrapper:

```
 **Output:**

```json
[
  { "topic": "...", "status": "NEW", "selectedArticleIds": ["candidate-1", "candidate-2"] },
  ...
]
```
```

Two separate defects turned that into a lost episode.

**The parser accepts only one shape.** `BeanOutputConverter` is handed the raw text, so it stops on the `*` of the prose lead-in. The salvage path then scans for a `clusters` property name, which a bare array does not have, and recovers nothing. A response carrying 49 complete clusters produced zero.

**The retries were replays, not calls.** `CachingChatModel` keys on prompt text and refuses only blank completions, so the unparseable answer was cached. Both retries of episode 202 finished in four milliseconds each without touching the model, and a manual retry half an hour later did the same. The exponential backoff accomplished nothing, and the episode could not be recovered by retrying at all. `ArticleScoreSummarizer` already solved this for the scoring stage; dedup never got the same treatment.

## What Changes

- `TopicDedupFilter` SHALL parse from the first brace or bracket in the response, ignoring any prose lead-in or markdown code fence the model wrapped it in. Reading a single JSON value ends the strict parse, so a closing fence or trailing chatter is ignored without having to locate where it starts.
- `TopicDedupFilter` SHALL accept a bare cluster array as well as the `{ "clusters": [...] }` object the prompt asks for. The salvage path SHALL recover complete clusters from either shape.
- `TopicDedupFilter` SHALL send a distinct prompt on every attempt, appending a correction naming the attempt number from the second attempt on. This busts the cache key, so each retry is a real call, and tells the model that its previous answer could not be parsed.
- The failure message SHALL stop claiming the response was truncated, since an off-schema response reaches the same branch.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `article-dedup-filter`: the salvage requirement gains the payload extraction, the bare-array shape, and the distinct-prompt-per-attempt behaviour.

## Impact

- Backend: `TopicDedupFilter` (new `jsonStart`, `parseEitherShape`, `promptForAttempt` and `jsonOnlyCorrection`; `parseOrSalvage` and `salvageClusters` both read from the first brace or bracket; `parseOrSalvage` becomes `internal` so the whole parse path is testable).
- Tests: `TopicDedupFilterTest` gains coverage for the fenced-prose shape, the bare array, a truncated bare array, chatter after the JSON, a response holding no JSON, and the retry prompts.
- No schema, API, frontend, or configuration change.
- Operational, alongside this change: the affected podcast's dedup override pointed at `~deepseek/deepseek-v4-flash-latest`, the OpenRouter alias that follows the newest V4 Flash release. Its current target is what answered off-schema. The override was repointed at the pinned `deepseek/deepseek-v4-flash-0731`, matching the `app.llm.defaults.dedup` default, and the poisoned `llm_cache` row was deleted. Episode 202 then deduped 93 candidates into 62 clusters in 16.6 seconds on the first attempt. The `filter` stage of that podcast still points at the alias and carries the same risk; scoring survives it because it already escalates its retry prompts.
