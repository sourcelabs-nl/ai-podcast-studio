## Context

This is the half of the original scoring proposal that needs no model. Separating it is not only sequencing: the two layers have different properties and should not share a lifecycle. Deterministic metrics are free, stable and reproducible forever; a judged score costs money per script, drifts between model versions and has to be versioned and stored. Binding them together would have made the cheap half wait on the expensive half's persistence, migration and calibration.

## Decisions

**Nothing is persisted.**

The score in the judged layer is stored because it is expensive and unstable. A metric is neither: recomputing it from the stored script costs microseconds and always agrees with the script it describes. A table would add a copy that can silently disagree after a script is edited, and a scorer version to manage, for no gain.

**Metrics describe, they do not grade.**

No thresholds, no pass or fail, no composite number. The point is to see the distribution over the archive and then decide what good looks like, not to encode today's guess about it into the code. A threshold added now would be the same unverified opinion the metrics exist to replace.

**Backchannels are reported as candidates.**

The device is defined by intent (a token of pure listening) and detectable only by shape (a short interviewer turn between two expert turns). Those differ: a two-word question has the same shape. Reporting candidates with their turn indices keeps the metric honest and lets a reader check a handful rather than trust a count.

**Consecutive same-speaker turns are located, not flagged.**

They are legitimate after a backchannel and a defect otherwise, and only the text distinguishes the two. The metric reports where they are so they can be looked at.

## Non-Goals

- Judging anything that needs reading comprehension: whether a promise is genuinely deferred, whether a line is funny. That is the judge's half.
- A quality gate in CI. These numbers describe an episode; they do not yet know what a good one looks like.
