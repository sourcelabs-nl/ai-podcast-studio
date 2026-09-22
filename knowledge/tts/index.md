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

- [Pace-reducing steering instructions cost about ten seconds a turn](pace-reducing-steering-instructions.md)  
  Read when adding or debugging a pace or delivery cue in a TTS request.
- [A steering instruction on a speaker's first turn over-commits](unanchored-opening-cues.md)  
  Read when a steering instruction sounds over the top on a speaker's opening line.
- [Phoneme spans are unreliable under CREATIVE delivery mode](phoneme-spans-under-creative.md)  
  Read when a name is mispronounced in a rendered episode, or deciding how to fix a pronunciation.
- [An unlisted sound tag becomes a steering instruction](unlisted-sound-tags.md)  
  Read when a turn's delivery turns strange partway through, or adding a new sound tag.
