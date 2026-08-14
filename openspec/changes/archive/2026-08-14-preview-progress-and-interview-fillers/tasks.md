## 1. Preview progress reaches the UI

- [x] 1.1 Hoist the SSE `eventName` out of the read loop so an event split across a chunk boundary keeps its name
- [x] 1.2 Add a `deduplicating` branch to the progress display
- [x] 1.3 Show scoring as a running `scoredCount`/`articleCount` count, falling back to the total when the counter is absent
- [x] 1.4 Mark the preview proxy route dynamic and send `X-Accel-Buffering: no`

## 2. Interview scripts use filler words

- [x] 2.1 Route `PodcastStyle.INTERVIEW` to the casual guidance alongside `CASUAL` and `DIALOGUE`
- [x] 2.2 Cover it with a test asserting interview guidelines include filler words and do not suppress them

## 3. Specs

- [x] 3.1 Record the `deduplicating` progress event in `sse-preview`
- [x] 3.2 Record the stage coverage and scoring counter in `frontend-upcoming-episode`
- [x] 3.3 Record the interview style's filler-word guidance in `inworld-tts`

## 4. Verification

- [x] 4.1 `mvn test` green (1060 tests)
- [x] 4.2 `npx tsc --noEmit` clean
- [ ] 4.3 Run a preview from the UI and confirm the stage labels advance through aggregating, scoring with a live count, deduplicating, and composing
- [ ] 4.4 Compose an interview episode and confirm the script contains filler words
