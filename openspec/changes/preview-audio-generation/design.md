## Context

Script preview already produces a script that belongs to no episode: nothing is written to the database, no cost is recorded, and the result lives only in the browser tab that asked for it. Preview audio extends that same idea to the TTS half of the pipeline. The constraint that shapes everything here is that a preview has no episode row to hang state, ownership, or a lifetime on.

## Goals / Non-Goals

**Goals:**
- Let the user hear the configured voices before committing to an episode.
- Exercise the real provider path, so the audition reflects delivery mode, steering, synthesis context, pronunciations, and post-processing exactly as an episode would.
- Make the expensive action deliberate and informed, and the cheap one frictionless.
- Leave no state behind: no episode, no attached audio, no persisted cost, and no file that outlives its usefulness.

**Non-Goals:**
- Creating or pre-filling an episode from a preview.
- Persisting preview TTS cost or rolling it into any budget gate.
- Regenerating or replacing an existing episode's audio, which `audio-regeneration` already covers.

## Decisions

**The sample is synchronous and the full run is streamed.**
They differ by two orders of magnitude in duration, and that difference is what decides the transport, not a preference for one style. A sample is one chunk: a few seconds, well inside any request timeout, and its payload is small enough to return in the response body, so a plain `POST` returning `audio/mpeg` is the whole design. A full script is roughly fifty chunks and runs for minutes, which exceeds the Spring MVC async-request timeout — we hit exactly that on the manual-generation endpoint and moved it to a background run with SSE. So the full run reuses the preview endpoint's shape: an `SseEmitter` on a dedicated `Dispatchers.IO` scope, `progress` events, a `result` event carrying an id, `complete`, `error` on failure, and the same 15-second heartbeat that keeps intermediaries from timing the stream out. The finished bytes are fetched with a separate `GET`, because an SSE stream cannot carry them and base64 in an event would triple the payload.

**The sample is chosen by whole speaker turns, never by cutting text.**
The obvious implementation, "take the first N characters", produces a sample that stops mid-sentence and, on a dialogue script, may never reach the second voice — which is precisely the thing the user wants to hear. So the slice is one provider chunk's worth of *whole turns*. For a monologue there are no turns and `TextChunker`'s own first boundary is already the natural cut: it splits at a paragraph break where one exists and only degrades to a mid-sentence cut for a single unbreakable run longer than a chunk. For dialogue and interview, turns are accumulated whole while they fit, and if one chunk has not reached a second distinct speaker the selection extends contiguously to that speaker's first turn — even when that overshoots the chunk. Overshooting is the right trade: a two-voice sample that costs slightly more than a cent is worth far more than a one-voice sample that costs slightly less. The extension stays contiguous rather than jumping to the next new speaker, so the audition plays as an unbroken stretch of conversation.

**The slice is re-tagged and fed through the normal provider path.**
The selector emits `<role>text</role>` and hands it to `TtsProviderFactory.resolve(podcast)` like any other script. Synthesising the slice through a separate, simpler path would be faster to write and would defeat the point: the sample would not tell the user what their delivery mode, steering instructions, or pronunciation entries actually sound like.

**Ownership is expressed by the directory, not by a table.**
A preview file has no row, so there is nowhere to record who owns it. Instead the file lives at `{directory}/{podcastId}/{audioId}.mp3` and is only ever resolved from the podcast id in the request path — which the controller has already checked the caller owns, the same check every other podcast-scoped endpoint makes. A second user asking for another user's `audioId` resolves a path under their own podcast that does not exist, and gets a 404. The id itself is a random UUID, so it cannot be guessed, and anything that is not a well-formed UUID is refused before it reaches the filesystem, which also forecloses traversal through the id. Adding a table to track ownership was rejected: it would give preview audio the persistence the feature exists to avoid, and it would need its own cleanup anyway.

**A TTL sweep, because nothing else will ever delete these files.**
Episode audio is deleted when its episode is, and `EpisodeCleanup` handles the retention window. Preview audio has no such anchor, so age is the only signal available. A `@Scheduled` sweep on a configurable cron deletes files past `app.preview-audio.retention-minutes` and removes emptied podcast directories. Two hours is generous for "I generated this and want to listen to it now" and short enough that the directory cannot accumulate. The sweep creates no thread pool: it runs on the existing Spring `TaskScheduler` and delegates all filesystem work to the service, so the scheduler stays an entry point.

**Preview TTS cost is deliberately not persisted.**
Nothing records it: not an episode row, not a budget gate, not the cost tab. This mirrors script preview, which likewise spends LLM tokens without recording them, and it follows from the absence of an episode — there is no row to attribute the spend to, and inventing one would contradict the point of a preview. The cost is instead surfaced *before* the spend, through the estimate endpoint and the confirmation dialog, which is where it can actually change a decision. The trade-off is real and accepted: preview spend is invisible in the cost reporting, which is why the cheap action is the default and the expensive one is gated.

**Progress is reported through the provider, not inferred around it.**
Only the provider knows how many chunks a script became and when each finished, so `TtsRequest` gained an optional `TtsProgressListener` and every provider invokes it as chunks complete. The alternative — chunking the script in the preview service and calling the provider per chunk — would have bypassed dialogue voice routing, steering re-emission, and synthesis context, making the full preview sound unlike the episode it is previewing. Inworld synthesises chunks concurrently, so it counts completions rather than reporting the chunk index, and the count therefore rises monotonically even though chunks finish out of order. That concurrency also reaches the emitter: `SseEmitter` is not safe for concurrent `send()`, so every write to a preview audio stream — progress, result, and heartbeat alike — is serialised through one lock. A send that fails is swallowed: a dropped progress event must not abort a synthesis run that is minutes in.

**The estimate counts spoken characters only.**
Speaker tags route a turn to a voice and are never sent to the provider, so a dialogue estimate sums the text inside the tags rather than the raw script length. The rate comes from the podcast's configured TTS model, falling back to the provider's first configured rate when the podcast names no model — the same fallback `CostEstimator.estimateTtsCostCents` already applies for episodes. The estimate is therefore approximate in exactly the ways episode cost already is.

**`DEEP_DIVE` gets the conversational reading.**
It was the last style falling through `scriptGuidelines` to no guidance at all. Whether a deep dive should read as conversational or as formal is a content decision rather than a defect, and the choice made here is conversational: a long-form exploratory episode benefits from the disfluencies that make synthesised speech sound human, in the same way casual, dialogue, and interview do.

## Risks / Trade-offs

- **Preview spend is untracked** → Accepted, and mitigated by putting the number in front of the user before the spend rather than in a report afterwards. The cheap action needs no confirmation because it is about a cent; the expensive one cannot be triggered without seeing its cost.
- **A dialogue sample can exceed one chunk** → Bounded by the second speaker's first turn, so the overshoot is one turn, not the rest of the script.
- **A full run holds an SSE connection for minutes** → Same exposure as the existing script preview endpoint, on its own supervised scope, with the same heartbeat and a 30-minute emitter timeout.
- **A file can outlive its browser tab by up to the retention window** → Deliberate: the user may want to replay it. It is unguessable and unreachable from another podcast for that whole window.
- **Concurrent full runs cost real money with no budget gate** → The confirmation dialog is the only gate. A per-podcast rate limit was considered and left out as premature for a manually triggered action.
