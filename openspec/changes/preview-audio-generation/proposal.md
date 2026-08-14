## Why

The Script tab can already show a previewed script before an episode exists, which answers "is this worth publishing?" for the writing. It answers nothing about the listening. Voice choice, delivery mode, steering, speaking rate, and the pronunciation dictionary are all configured blind: the only way to hear any of them is to generate a real episode, which creates a row, spends the full TTS budget, and has to be discarded afterwards.

Two different questions need answering, and they have very different price tags. "Does this voice sound right?" is settled by a few seconds of audio and costs about a cent. "Does the whole thing hang together?" needs the full script, takes minutes, and costs real money. Collapsing them into one action would either make the cheap check expensive or make the expensive one accidental.

Separately, `DEEP_DIVE` is the last style that reaches `InworldTtsProvider.scriptGuidelines` and falls through to no style guidance at all, so a deep-dive script is composed without the filler-word instruction that makes synthesised speech sound human.

## What Changes

- A sample endpoint synthesises the opening slice of a previewed script and returns the audio directly. The slice is about one provider chunk, aligned to the end of a speaker turn, and for dialogue and interview styles it always covers two distinct speakers so both voices are auditioned.
- A full-audio endpoint streams per-chunk progress over SSE and hands back an opaque id; a separate endpoint streams the finished file. A real script is dozens of chunks and would exceed the Spring MVC async-request timeout as a plain request.
- An estimate endpoint reports the character count and cost of a full synthesis, so the expensive action sits behind an informed confirmation.
- Preview audio is written to a configurable temp directory, is reachable only through the podcast that owns it, and is swept once it exceeds a configured age.
- `TtsRequest` carries an optional progress listener so a caller streaming to a client can report chunk completions. Every provider reports through it.
- The Script tab gains "Play Sample" and "Generate Full Audio" buttons, a cost confirmation dialog, per-chunk progress, and an inline player.
- `DEEP_DIVE` joins the casual branch of the Inworld script guidelines and receives the filler-word instruction.

## Capabilities

### New Capabilities

- `preview-audio`: synthesising audio from a script that belongs to no episode, as a cheap sample or a streamed full run, with an up-front cost estimate and a swept temp store.

### Modified Capabilities

- `frontend-upcoming-episode`: the Script tab can audition and generate audio for the previewed script.
- `inworld-tts`: the deep dive style receives the filler-word guidance.
- `tts-provider-abstraction`: `TtsRequest` carries an optional progress listener that providers invoke as chunks complete.

## Impact

- Backend: new `PreviewAudioController`, `PreviewAudioService`, `PreviewAudioStore`, `PreviewAudioScheduler`, `PreviewSampleSelector`; `TtsProvider.kt` gains `TtsProgressListener`; all four providers report progress; `InworldTtsProvider.scriptGuidelines`; `AppProperties` and `application.yaml` gain `app.preview-audio`.
- Frontend: `frontend/src/app/podcasts/[podcastId]/upcoming/page.tsx`, a new SSE proxy route at `frontend/src/app/api/users/[userId]/podcasts/[podcastId]/preview/audio/route.ts`, `frontend/src/lib/types.ts`.
- No database schema change: preview audio has no row, and its TTS cost is deliberately not persisted, exactly as script preview already behaves.
- No episode is created and no audio is attached to any episode.
