## 1. Persistence

- [x] 1.1 Add Flyway `V63__add_tts_calls_to_episode.sql` adding nullable `tts_calls INTEGER` to `episodes`
- [x] 1.2 Add `ttsCalls: Int? = null` to the `Episode` entity
- [x] 1.3 Persist `ttsCalls = ttsResult.audioChunks.size` in `TtsPipeline.generate` and `generateForExistingEpisode`

## 2. API

- [x] 2.1 Add `calls: Int` to `TtsCostResponse`
- [x] 2.2 Map `calls = ttsCalls ?: 0` in `PodcastMappers.buildCosts`

## 3. Frontend

- [x] 3.1 Add `calls: number` to the `TtsCost` interface in `types.ts`
- [x] 3.2 Render `formatInt(costs.tts.calls)` in the TTS row Calls cell of `costs-tab.tsx`

## 4. Config tweaks (related, already applied)

- [x] 4.1 Add `z-ai/glm-5.2` pricing entry ($1/$4 per MTok, DeepInfra) and switch `compose` default to it
- [x] 4.2 Correct `inworld-tts-2` `cost-per-million-chars` from 35.00 to 25.00

## 5. Verification

- [x] 5.1 Add mapper test assertions for `tts.calls` (populated and null→0)
- [x] 5.2 `mvn test` green (925 tests), frontend `tsc --noEmit` clean, migration applied on restart
