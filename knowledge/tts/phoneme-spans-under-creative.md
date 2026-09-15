---
okf_version: "0.2"
type: finding
title: Phoneme spans are unreliable under CREATIVE delivery mode
status: stable
model_version: inworld-tts-2
method: >
  Eight byte-identical requests carrying an IPA phoneme span for one name, under
  deliveryMode CREATIVE and again under STABLE. Outputs listened back.
stale_after: 2027-03-15T00:00:00Z
generated:
  by: claude-opus-5
  at: 2026-09-15T00:00:00Z
verified:
  - by: claude-opus-5
    at: 2026-09-15T00:00:00Z
    note: reproduced across eight identical requests
---

Three of eight identical requests mangled the name under `CREATIVE`; the same
requests under `STABLE` were clean. The failing request was byte-identical to a
probe that had succeeded, so the failure is in the engine's sampling and not in
the request.

The practical reading is that a phoneme span is a hint under `CREATIVE`, not an
instruction, and pronunciation that has to be right cannot be bought with one.
Spelling the word so the engine reads it correctly without a phoneme span is the
more reliable route: see [[initialisms-stay-capitalised]].

Related: [[unlisted-sound-tags]]
