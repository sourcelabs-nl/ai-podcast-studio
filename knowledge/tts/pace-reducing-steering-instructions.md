---
okf_version: "0.2"
type: finding
title: Pace-reducing steering instructions cost about ten seconds a turn
answers: adding or debugging a pace or delivery cue in a TTS request
status: stable
model_version: inworld-tts-2
method: >
  One 601-character turn, rendered four times with no steering instruction and
  four times with a pace-reducing one, all other request fields identical.
  Durations measured on the returned audio.
stale_after: 2027-03-15T00:00:00Z
generated:
  by: claude-opus-5
  at: 2026-09-15T00:00:00Z
verified:
  - by: claude-opus-5
    at: 2026-09-15T00:00:00Z
    note: measured directly against the live API
---

A steering instruction asking for a slower or calmer delivery stretches the turn
far more than it reads: the plain arm rendered in 35.6 to 36.7 seconds, the cued
arm in 45.6 to 47.9 seconds. The two ranges do not overlap, so the effect is
larger than the run-to-run variation at this turn length.

Anchoring the request against previous turns through `synthesisContext` does not
soften it. The engine treats the instruction as a property of the whole turn
rather than as a nudge, so the cost scales with turn length and lands on exactly
the long expert turns where pace matters least.

The consequence is that pace cues are not free colour: an episode that carries
them on several turns runs minutes longer for no gain the listener asked for.
See the archived change `drop-pace-reducing-directions` for what was removed.

Related: [[unanchored-opening-cues]]
