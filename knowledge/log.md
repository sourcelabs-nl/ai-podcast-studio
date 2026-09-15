# Log

Newest first. Each entry begins with `## [YYYY-MM-DD] <operation>` so recent
activity can be read with `grep "^## \[" knowledge/log.md | head -10`.

## [2026-09-15] Record

Speaker alternation is now repaired in the compose clean-up chain and logged.
Rewrote the alternation entry to state what the pipeline does rather than what
it fails to do.

## [2026-09-15] Lint

First pass over the seeded bundle. All cross-references resolve and no entry is
unreachable from an index. Corrected em-dashes in the root index against the
project's writing conventions. Confirmed the log prefix is greppable and that
nothing under `src/` or `frontend/` reads `knowledge/`.

## [2026-09-15] Record

Seeded the bundle: skeleton and four sections, the TTS findings from the Inworld
probes, the rationale behind four composition rules, and the reference material
behind the humor and anchor-story work.
