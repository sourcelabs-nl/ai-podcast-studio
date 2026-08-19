## Context

`InworldTtsProvider.prepareChunks` prepares a script (monologue) or a single speaker turn (dialogue, interview) for synthesis in three steps: post-process the text, chunk it at 1900 characters, then re-emit the active steering instruction at the head of every later chunk so the direction survives the splice into separate API requests.

Separately, `synthesizeAll` sends each chunk with `synthesisContext.previousRequests` carrying the preceding chunk texts. The first chunk of a script is the one request that gets no context. That asymmetry is what makes a delivery instruction behave differently there: with no preceding audio to imitate, the cue defines the read outright instead of colouring an established one.

The change is small, but where it sits in that three-step order determines whether it fixes one chunk or silently changes the whole episode, which is worth recording.

## Goals / Non-Goals

**Goals:**

- The script's first synthesis request carries no delivery instruction.
- Steering behaviour is unchanged everywhere else, including later chunks of the opening turn.
- The composer stops emitting an opening delivery direction in the first place.

**Non-Goals:**

- Restricting which delivery instructions the composer may write (mood cues stay allowed away from the opening).
- Changing `deliveryMode`, `temperature`, or any other synthesis parameter.
- Changing the opening-style variety descriptors in `PromptVarietyDescriptors`. A scene-setting cold open is fine when it is not read in a hushed voice.

## Decisions

**Strip after re-emission, not during post-processing.**

`InworldScriptPostProcessor.process` runs before chunking, on the whole text. For a monologue the whole script is one text, so an instruction at its start is the instruction that re-emission then carries onto every chunk. Dropping it there would remove the direction from the entire episode. Applying the strip to `chunks[0]` after `InworldSteering.reemitInstructions` gives the intended scope for free: chunk zero goes out plain, and chunks one onward already carry the re-emitted copy.

Alternative considered: keep the strip in `process` behind a flag. Rejected because it conflates "sanitize this text" with "this text happens to start a script", and because of the whole-episode side effect above.

**Scope by chunk index, not by turn.**

`prepareChunks` takes `isScriptOpening` and the provider passes `true` for the monologue script and for dialogue turn index `0`. Within that call only index `0` is stripped. Stripping the entire opening turn would be over-broad: a mid-turn cue in the cold open is already anchored by the audio of the chunk before it.

**Keep a leading sound tag.**

`stripLeadingInstruction` removes the leading tag only when `isSteeringInstruction` says it is a delivery direction. `[laugh]` or `[sigh]` opening a script is a sound, not a character direction, and does not have the unanchored-overshoot problem.

**Fix the prompt as well as the pipeline.**

The guidelines bullet stops the cue being written; the strip guarantees the outcome when the model ignores it. Prompt-only would drift; strip-only would leave the script text and the audio disagreeing about what the opening should sound like.

## Risks / Trade-offs

- **A deliberately steered opening is now impossible via script markup.** → Accepted. This is the point of the change, and an opening tone can still be set through the voice, `deliveryMode`, and the words themselves.
- **The strip is silent, so a composer that keeps emitting the tag leaves the script text and the audio out of step.** → The guidelines bullet makes it rare, and the tag remains visible in the stored `scriptText` for anyone comparing script to audio.
- **`stripLeadingInstruction` recognises only a tag at the very start.** → Matches Inworld's own rule that a delivery direction must be placed at the start of the input to take effect, so a cue further in was never governing the whole request anyway.
