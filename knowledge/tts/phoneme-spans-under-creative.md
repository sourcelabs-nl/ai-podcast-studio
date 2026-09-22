---
okf_version: "0.2"
type: finding
title: Phoneme spans are unreliable under CREATIVE delivery mode
answers: a name is mispronounced in a rendered episode, or deciding how to fix a pronunciation
status: stable
model_version: inworld-tts-2
method: >
  Eight byte-identical requests carrying an IPA phoneme span for one name, under
  deliveryMode CREATIVE and again under STABLE. Outputs listened back. Later, a
  further eight isolated requests through the preview-sample endpoint with the
  per-chunk STABLE guard in place, scored by MP3 duration against a plain-text
  control, plus a two-way listening comparison of the dictionary's own IPA value
  against an English-standard respelling of the same name.
stale_after: 2027-03-15T00:00:00Z
generated:
  by: claude-opus-5
  at: 2026-09-17T00:00:00Z
verified:
  - by: claude-opus-5
    at: 2026-09-15T00:00:00Z
    note: reproduced across eight identical requests
  - by: human:soudmaijer
    at: 2026-09-17T00:00:00Z
    note: listened to the A/B pair and judged the existing dictionary value correct
---

Three of eight identical requests mangled the name under `CREATIVE`; the same
requests under `STABLE` were clean. The failing request was byte-identical to a
probe that had succeeded, so the failure is in the engine's sampling and not in
the request.

The practical reading is that a phoneme span is a hint under `CREATIVE`, not an
instruction, and pronunciation that has to be right cannot be bought with one.
Spelling the word so the engine reads it correctly without a phoneme span is the
more reliable route: see [[initialisms-stay-capitalised]].

## The per-chunk STABLE guard does not close it

Narrowing only the chunks that carry a phoneme to `STABLE` reduces the failure
but has not eliminated it in production. An episode rendered with that guard
active still read a sign-off's phoneme span out literally, as its delimiters and
symbols rather than as the name.

That failure does not reproduce in isolation: eight repeats of the same sign-off
line as a standalone one-chunk request produced no literal read, against a
control speaking the name as plain text. So whatever tips the engine over is
absent from a short standalone chunk and present in a full episode render. The
untested differences are chunk length and the `synthesisContext.previousRequests`
window a late chunk carries; a probe holding those fixed has not been run.

## The IPA inventory is not the cause

Inworld's custom-pronunciation documentation requires standard English IPA
phonemes only, which makes a Dutch-vowel value such as `/jɑrnoː/` look like the
obvious defect. It is not: asked to compare that value against an
English-standard respelling of the same name, rendered through the real voice
and settings, a listener judged the existing Dutch-vowel value the correct one
and the English-standard one wrong. The documented constraint does not predict
which value this engine speaks correctly, so a pronunciation value is settled by
listening, not by reading the spec.

Related: [[unlisted-sound-tags]], [[unanchored-opening-cues]]
