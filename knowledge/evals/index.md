---
okf_version: "0.2"
type: index
title: Experiments
status: stable
generated:
  by: claude-opus-5
  at: 2026-09-15T00:00:00Z
---

# Experiments

A result kept here is only usable if its conditions are kept with it: what
varied, what was held fixed (at least the episode date and the prompt variety
selection), how many runs were made, how many of those reached the model rather
than the LLM cache, and whether the cache was bypassed. A result without them is
indistinguishable from a single cached sample replayed.

Negative results are recorded on the same terms as positive ones.

- [Structural baseline over the 18 most recent episodes](structure-baseline-2026-09.md)
- [Episode 208 against 209, an observation rather than an experiment](episode-208-vs-209.md)
- [The judge's anchors match the hand audit of episode 209](judge-calibration-episode-209.md)
- [The judged baseline over the archive](judged-baseline-2026-09.md)
- [Repeating a prompt variant needs an explicit cache bypass](evaluation-run-cache-bypass.md)
- [Removing CURIOSITY HOOKS cuts the cheap promises, not the real ones](curiosity-hooks-ablation.md)
- [Structured output fixes the speaker tags and breaks the length](structured-output-for-scripts.md)

The [[curiosity-hooks-conflict]] ablation has been run: see
[[curiosity-hooks-ablation]].
