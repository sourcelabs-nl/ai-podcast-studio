---
okf_version: "0.2"
type: index
title: TTS engine findings
status: stable
generated:
  by: claude-opus-5
  at: 2026-09-15T00:00:00Z
---

# TTS engine findings

Measured behaviour of Inworld TTS-2, the engine every episode is rendered with.
Each entry states how it was measured and expires: the vendor changes the model
without changing the model id, so a finding past its `stale_after` is a
hypothesis to re-measure, not a fact.

- [Pace-reducing steering instructions cost 10 seconds a turn](pace-reducing-steering-instructions.md)
- [An opening steering instruction over-commits](unanchored-opening-cues.md)
- [Phoneme spans are unreliable under CREATIVE](phoneme-spans-under-creative.md)
- [An unlisted sound tag becomes a steering instruction](unlisted-sound-tags.md)
