## Why

Two defects surfaced while previewing an upcoming episode.

The Script tab shows no progress at all during a preview that runs for minutes. The backend streams the stages correctly, but the client drops them. The SSE reader resets the current event name on every network read, and an event arrives as an `event:` line followed by a `data:` line, so whenever a chunk boundary falls between the two the name is lost and the data line matches no branch. The client also has no branch for the `deduplicating` stage, which on a real run is the longest single wait.

Interview episodes read as flat because the composer is never told to use filler words. The Inworld guidance on natural speech relies on disfluencies, and the script guidelines carry that instruction, but they attach it only to the casual and dialogue styles. Interview and deep dive fall through to no style guidance at all, so an interview script is composed without the very markers that make synthesised speech sound human.

## What Changes

- The preview SSE reader keeps the current event name across reads, so an event split across a chunk boundary is still recognised.
- The preview progress display covers every stage the backend emits, including `deduplicating`, and shows scoring as a running `scoredCount`/`articleCount` count rather than a static total.
- The preview proxy route is marked dynamic and sets `X-Accel-Buffering: no`, so no layer in front of the app batches a stream whose entire value is arriving live.
- The interview style receives the same filler-word guidance as casual and dialogue.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `frontend-upcoming-episode`: the preview progress display covers every emitted stage and reports scoring progress as a running count.
- `sse-preview`: the documented progress events include the `deduplicating` stage the pipeline already emits.
- `inworld-tts`: the interview style receives the filler-word guidance.

## Impact

- Frontend: `frontend/src/app/podcasts/[podcastId]/upcoming/page.tsx`, `frontend/src/app/api/users/[userId]/podcasts/[podcastId]/preview/route.ts`.
- Backend: `InworldTtsProvider.scriptGuidelines`.
- Deep dive remains without style-specific guidance. Whether it should read as conversational or formal is a content decision, not a defect, and is left unchanged.
- No API contract, schema, or cost behaviour changes.
