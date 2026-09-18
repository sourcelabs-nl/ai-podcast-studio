## Context

See proposal.md - Why for the motivation and the measurements.

Two facts about the current code shape the design:

`TtsResult.audioChunks` (`TtsProvider.kt:40`) is a `List<ByteArray>` with no voice
attribution, and `InworldTtsProvider.generateDialogue` (`InworldTtsProvider.kt:136-143`)
flattens the parsed turns into one chunk list precisely so that every chunk can be
synthesized in parallel. The voice per chunk is known at that point (`ChunkWork.voiceId`)
and thrown away immediately after.

`AudioConcatenator.concatenate` (`AudioConcatenator.kt:29`) writes the chunks to a temp
directory and runs ffmpeg's concat demuxer with `-c copy`. That stream copy is why the
prepended silence has to be encoded to match the chunks, which is where `probeFormat` and
its fallbacks come from (`AudioConcatenator.kt:79-111`).

## Goals / Non-Goals

Goals:
- Voice attribution survives from synthesis to concatenation.
- Loudness correction is computed per voice and verified, not applied open-loop.
- A failure to normalize degrades to today's behavior rather than losing an episode.

Non-Goals:
- No compression, EQ, de-essing, or any other tonal processing. Only gain and a peak
  limiter.
- No per-turn or per-chunk levelling. See the specs for why voice-level is the right
  granularity.
- No change to which voices a podcast uses, and no change to synthesis itself.
- No re-normalization of already published episodes.

## Decisions

**Carry the voice on the chunk, not alongside it.** `TtsResult.audioChunks` becomes
`List<AudioChunk>` where `AudioChunk` holds the bytes and the `voiceId`, and
`AudioConcatenator.concatenate` takes that list. The alternative, a parallel
`List<String>` of voice ids or a `Map<Int, String>` index, lets the two lists drift out
of sync; chunks are produced concurrently and reordered by index, so a positional
side-channel is exactly the kind of thing that breaks quietly. A single-voice provider
sets the same id on every chunk, which keeps the monologue path on the same code.

**Gain plus limiter, applied twice, rather than `loudnorm`.** `loudnorm` is the obvious
tool and it is the wrong one here: measured against our six-turn sample, a two-pass
`loudnorm` with `linear=true` capped the gain at the peak ceiling and left a 2.3 LU
spread between the voices, barely better than the 2.2 LU we started with. Measuring with
`ebur128` and applying a flat `volume=NdB` hits the target exactly but pushed the true
peak to +4.2 dBFS on our sample, which clips. The combination that worked: flat gain to
the target, then `alimiter` for the ceiling, then measure again and apply the residual,
because the limiter's loudness cost depends on how peaky the voice is and is not
predictable up front. That sequence took the sample from 2.2 LU spread to 0.2 LU with
true peaks at or below -1.3 dBFS.

**Measure per voice over the concatenation of that voice's chunks, not per chunk
averaged.** Integrated loudness is not linear in the chunks: averaging per-chunk LUFS
weights a two-second chunk like a thirty-second one. Concatenating a voice's chunks into
one temporary file and measuring that gives the correct integrated value, and the gating
that R128 applies to silence then works across chunk boundaries as intended.

**Re-encode in the concat step.** Normalization already forces a decode, so the concat
demuxer's `-c copy` becomes an encode to one target format for every segment. The
`probeFormat` logic stays, but its job changes from "match the silence to the chunks" to
"choose the output format", preserving each provider's native sample rate. Its fallbacks
are unchanged and still warn.

**Target -16 LUFS, ceiling -1.0 dBFS.** This is the common streaming/podcast target, and
the tolerance in the specs (0.5 LU) is well under the roughly 1 LU at which a level
difference becomes noticeable.

## Risks / Trade-offs

**Generation gets slower** → Every chunk is now decoded, measured, and re-encoded, and
each voice is measured twice. The work is linear in audio length and ffmpeg does it at
many times realtime (our 102-second sample measured in well under a second per pass),
so the cost is small next to synthesis, which is network-bound and takes minutes.

**A very quiet voice needs a large boost, which lifts its noise floor** → The limiter
controls peaks but nothing controls hiss. The 4.8 LU correction the older clone needs is
within the range where this is inaudible on speech, but a voice that measured, say, 15 LU
below target would be a synthesis problem, not a mastering one. The normalization logs
the gain it applies per voice so an extreme correction is visible.

**Re-encoding is generation loss** → The chunks are already lossy MP3 and get decoded and
re-encoded once, at the same bit rate. One generation of MP3 transcoding on speech at
128kbps is not audible, and the alternative (normalizing before the provider encodes) is
not available to us since providers return MP3.

**Behavior change applies to previews too** → `PreviewAudioService` and
`PreviewAudioStore` use the same concatenator, which is correct: a preview that is not
levelled like the episode is a misleading preview.

## Migration Plan

No data migration and no schema change. The change is confined to audio assembly, so it
takes effect for episodes generated after deploy; existing published audio is untouched.
Rollback is a revert, since nothing persists in a new shape.

The `AudioChunk` type crosses the `TtsProvider` interface, so all four implementations
and the three `concatenate` callers change together in one commit; there is no
intermediate state where the build is green with only some of them converted.
