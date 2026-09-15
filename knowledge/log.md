# Log

Newest first. Each entry begins with `## [YYYY-MM-DD] <operation>` so recent
activity can be read with `grep "^## \[" knowledge/log.md | head -10`.

## [2026-09-15] Record

Judged the whole archive and recorded the baseline. Humour is one speaker's job
in 91 of 162 episodes and 70 defer nothing, so the two rules written against
episode 208 now have numbers to move. Recorded alongside it why repeating a
prompt variant needs an explicit cache bypass.

## [2026-09-15] Record

Recorded what a Narro feed carries after reading one live: no conversation id
exists, the reply header names the account rather than the tweet, and 28 of 29
replies in a 50-item sample are self-threads. That distribution is what the
reply-target threading rule rests on.

## [2026-09-15] Record

Recorded the judge's first live calibration: its anchors for episode 209 match
the by-hand audit on every quantity, at 12 seconds and under a cent per script.

## [2026-09-15] Record

Recorded that turn length and episode length are judged by ear and will not
become gates, and that the length spread happens at a constant article count.

## [2026-09-15] Record

Implemented the deterministic metrics layer and ran it over the archive. Recorded
the baseline, which settles the episode length spread and shows that
backchannel-shaped turns predate the rule asking for them.

## [2026-09-15] Record

Split the scoring proposal into a free deterministic half and a judged half, so
the cheap layer does not wait on the expensive layer's migration and
calibration.

## [2026-09-15] Record

Added the backchannel device and the reference-show turn-length finding it rests
on, mined from the 2026-09-14 session where the reference analysis was done.

## [2026-09-15] Record

Corrected what the episode 209 defect actually is: the overlap between two
adjacent turns, not the adjacency itself. The structural merge built against the
earlier reading was removed again.

## [2026-09-15] Lint

First pass over the seeded bundle. All cross-references resolve and no entry is
unreachable from an index. Corrected em-dashes in the root index against the
project's writing conventions. Confirmed the log prefix is greppable and that
nothing under `src/` or `frontend/` reads `knowledge/`.

## [2026-09-15] Record

Seeded the bundle: skeleton and four sections, the TTS findings from the Inworld
probes, the rationale behind four composition rules, and the reference material
behind the humor and anchor-story work.
