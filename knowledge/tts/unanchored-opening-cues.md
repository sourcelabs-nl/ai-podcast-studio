---
okf_version: "0.2"
type: finding
title: A steering instruction on a speaker's first turn over-commits
answers: a steering instruction sounds over the top on a speaker's opening line
status: stable
model_version: inworld-tts-2
method: >
  Rendering the same steering instruction on a speaker's first turn, where no
  previous requests exist to anchor it, and on a later turn of the same episode
  where synthesisContext carries prior audio.
stale_after: 2027-03-15T00:00:00Z
generated:
  by: claude-opus-5
  at: 2026-09-15T00:00:00Z
verified:
  - by: claude-opus-5
    at: 2026-09-15T00:00:00Z
    note: heard in rendered episodes and reproduced on demand
---

On a speaker's first turn there is nothing in `synthesisContext.previousRequests`
for the engine to anchor against, so a steering instruction is the only voice
direction it has and it commits to the extreme of that direction. The same
instruction on a later turn reads as a modulation of a voice already established
and lands where it was meant to.

The rule that follows is per speaker, not per episode: strip a steering
instruction from each speaker's own first turn and keep it from their second
onwards. A tag surviving on the second turn of a speaker is correct behaviour,
not a leak.

Related: [[pace-reducing-steering-instructions]], [[phoneme-spans-under-creative]]
(whose unreproduced failure names this same `synthesisContext.previousRequests`
window as an untested variable)
