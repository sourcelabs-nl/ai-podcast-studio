---
okf_version: "0.2"
type: finding
title: Repeating a prompt variant needs an explicit cache bypass
status: stable
method: >
  Read from the cache key itself rather than observed in a wrong result, then
  pinned by a test that two identical evaluation calls reach the model twice.
stale_after: 2027-09-15T00:00:00Z
generated:
  by: claude-opus-5
  at: 2026-09-15T00:00:00Z
---

The LLM cache keys on the model plus the `USER` and `SYSTEM` prompt text. The
temperature is not part of the key. So k repetitions of one prompt variant are
one model call and k-1 replays of its answer, and the spread those repetitions
exist to measure is zero by construction.

This matters because the composer is not deterministic and a single generation
therefore cannot attribute a difference to a prompt. Any comparison between two
variants is a comparison of samples, and without a bypass the samples after the
first are copies.

An evaluation run therefore composes with the cache neither read nor written:
not read so each repetition is independent, not written so an experiment never
displaces the answer a production generation would read. It is requested per run
rather than configured globally, because turning the cache off everywhere would
make ordinary retries and regenerations pay for work already done.

A consequence worth stating: the "did the bypass take effect" flag stored on a
run is false on every row written today, since disabling the read is exactly what
guarantees the bypass holds. It is recorded so a run stays interpretable, not as
a live check.

The baseline this unblocks is [[judged-baseline-2026-09]]. The first question
queued for it is the [[curiosity-hooks-conflict]] ablation.
