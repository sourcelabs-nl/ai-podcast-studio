---
okf_version: "0.2"
type: finding
title: An unlisted sound tag becomes a steering instruction
status: stable
model_version: inworld-tts-2
method: >
  Rendering turns carrying sound tags outside the documented set and comparing
  the delivery of the rest of the turn against the same turn without them.
stale_after: 2027-03-15T00:00:00Z
generated:
  by: claude-opus-5
  at: 2026-09-15T00:00:00Z
verified:
  - by: claude-opus-5
    at: 2026-09-15T00:00:00Z
    note: heard in rendered output
---

A sound tag the engine does not recognise is not dropped. It is read as a
steering instruction and colours everything after it in that turn, so a stray
bracketed word changes the delivery of the sentences following it rather than
producing nothing.

This is why the pipeline normalises sound tags to the documented set instead of
passing them through: an unknown tag is not a no-op but a silent voice
direction. See the archived change `normalize-inworld-sound-variants`.

Related: [[phoneme-spans-under-creative]]
