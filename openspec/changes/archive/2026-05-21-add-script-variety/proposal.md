## Why

The last 10 generated episodes read almost identical at the seams: identical hook formula, identical "Welcome to..." opener, identical "Coming up:" teaser shape, identical sign-off, even though the news content varies. Three root causes in the compose stage: (1) prompts are fully static with one-sentence style differentiation, (2) prompts hard-code verbatim example phrases that the LLM copies as scaffolding, and (3) no temperature is passed to the model (`OpenAiChatOptions` sets only `model`, so the model runs at provider defaults but is over-constrained by the prompt).

## What Changes

- Relax the prescriptive, example-laden prompts in `BriefingComposer`, `InterviewComposer`, and `DialogueComposer`. Remove verbatim example phrases (e.g. literal `"Coming up: AI agents going rogue..."`, literal `"Stephan, thanks as always"`, literal cliffhanger samples). Enforce the no-verbatim-examples rule via a hardcoded blocklist with a composer test that fails CI on regression.
- Pass `temperature` to the compose call. Add a per-podcast `composeSettings.temperature` field with default `0.95`, clamped to `[0.0, 2.0]`.
- Add `PromptVarietyPicker` keyed deterministically on `(podcastId, episodeDate)` that rotates **six** dimensions so day-to-day scaffolding differs: opening style, transition vocabulary, sign-off shape, "coming up" teaser shape (interview-only), topic-entry/handoff pattern, and penultimate-exchange shape. Same episode regenerated yields the same selection across all axes.
- Expose `composeSettings.temperature` in the podcast settings UI.

This change is independent of the history-lookback and deep-dive-research changes; it can land in any order.

## Capabilities

### New Capabilities
- `script-variety`: Deterministic per-episode rotation across six dimensions (opening style, transition vocabulary, sign-off shape, teaser shape, topic-entry pattern, penultimate-exchange shape), plus the compose-stage temperature control and the banned-phrase blocklist.

### Modified Capabilities
- `llm-processing`: Compose call now passes `temperature` in `OpenAiChatOptions`.
- `dialogue-composition`: Prompt uses the variety picker; verbatim example phrases removed.
- `interview-composition`: Prompt uses the variety picker; verbatim example phrases removed.
- `podcast-customization`: New `composeSettings` map (mirrors `ttsSettings` shape) with a `temperature` key for v1.
- `frontend-podcast-settings`: UI control for the compose temperature value inside `composeSettings`.
- `database-migrations`: New migration adding `compose_settings TEXT` (JSON map) to `podcasts`.

## Impact

- **Code** — `src/main/kotlin/com/aisummarypodcast/llm/{BriefingComposer,InterviewComposer,DialogueComposer,ComposerUtils}.kt`; new `llm/PromptVarietyPicker.kt`; updates to `store/Podcast.kt`, request/response mappers.
- **Database** — one new Flyway migration adding `compose_settings TEXT` (nullable JSON map) to `podcasts`. Default temperature `0.95` is applied at read time when the map is null or has no `temperature` key.
- **API** — `GET/PUT /users/{userId}/podcasts/{podcastId}` gains a `composeSettings` map field. For v1 only `temperature` is a recognised key; unknown keys are persisted but ignored by the compose stage.
- **Frontend** — `frontend/src/app/podcasts/[podcastId]/settings/page.tsx` gains the temperature input.
- **Dependencies** — none.
- **Cost** — no change.
