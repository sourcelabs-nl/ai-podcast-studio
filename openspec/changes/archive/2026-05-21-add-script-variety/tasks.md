## 1. Database migration

- [x] 1.1 Write `V53__add_podcast_compose_settings.sql` adding `compose_settings TEXT` (JSON) to `podcasts`
- [x] 1.2 Verify the migration applies cleanly against a copy of `./data/ai-summary-podcast.db`

## 2. Domain model + config

- [x] 2.1 Add `composeSettings: Map<String, String>?` to `Podcast` entity (mirrors `ttsSettings`; uses the existing JSON converter)
- [x] 2.2 Add `composeSettings: Map<String, String>?` to `CreatePodcastRequest`, `UpdatePodcastRequest`, and `PodcastResponse`; wire `orKeep` semantics in `update`, propagate in `create`
- [x] 2.3 Parse and validate the `temperature` key in the controller (parse to `Double`, clamp/reject if outside `[0.0, 2.0]`); persist the map as-is (unknown keys allowed)
- [x] 2.4 Add `app.briefing.default-temperature` to `AppProperties` defaulting to `0.95`

## 3. Prompt variety picker

- [x] 3.1 Implement `PromptVarietyPicker` with deterministic selection across all six axes keyed on `SHA-256(podcastId + ":" + episodeDate)`, using different byte ranges of the digest per axis
- [x] 3.2 Define six typed enums and their menu sets as private constants: `OpeningStyle`, `TransitionVocab`, `SignOffShape`, `TeaserShape`, `TopicEntryPattern`, `PenultimateExchangeShape`
- [x] 3.3 Expose `pick(podcastId, episodeDate): PromptVarietySelection` returning a `data class` with all six selections
- [x] 3.4 Unit-test the picker:
    - Same `(podcastId, episodeDate)` produces identical six-axis selection
    - Five consecutive dates produce at least 3 distinct opening-style AND 3 distinct sign-off-shape selections
    - Two different podcasts on the same date produce different selections (at least one axis differs)

## 4. Banned-phrase blocklist

- [x] 4.1 Create `BannedPrompts` (object or constant) listing the verbatim example phrases being removed (the "Coming up:..." example, cliffhanger samples, interruption samples, "Stephan, thanks as always", "But here's where it gets really interesting...")
- [x] 4.2 Add a parameterized composer test that builds prompts for every `PodcastStyle` (with a representative article fixture) and asserts the resulting prompt string does not contain any phrase from `BannedPrompts`

## 5. Composer refactor

- [x] 5.1 Refactor `BriefingComposer.buildPrompt` to consume the picker's first three axes (opening / transitions / sign-off); remove verbatim example phrases listed in `BannedPrompts`
- [x] 5.2 Refactor `DialogueComposer.buildPrompt` to consume opening / transitions / sign-off / topic-entry / penultimate-exchange (5 axes); remove verbatim example phrases
- [x] 5.3 Refactor `InterviewComposer.buildPrompt` to consume all six axes; remove the literal "Coming up:..." example, the literal cliffhanger samples, the literal interruption samples, and the "Stephan, thanks as always" beat
- [x] 5.4 Wire the resolved `composeSettings.temperature` into `OpenAiChatOptions.builder().temperature(...)` in all three composers
- [x] 5.5 Update or add composer tests asserting: temperature is passed (via `resolveTemperature` tests); the picker selection is reflected in each composer's prompt; no banned phrases appear (`ComposerBannedPromptsTest`)

## 6. Frontend

- [x] 6.1 Add the `composeSettings.temperature` numeric input to `frontend/src/app/podcasts/[podcastId]/settings/page.tsx` (step 0.05, range 0–2, placeholder "0.95")
- [x] 6.2 Manual smoke test: edit, save, reload, confirm value persists

## 7. Verification

- [x] 7.1 Run `mvn test` and ensure all tests pass
- [x] 7.2 Restart the app via `./stop.sh && ./start.sh`
- [x] 7.3 Generate 5 episodes back-to-back against the same article fixture; diff the first 200 chars and confirm at least 3 distinct opening styles
- [x] 7.4 Run `openspec validate add-script-variety --strict`
- [x] 7.5 Update `README.md` to document the `composeSettings.temperature` setting
