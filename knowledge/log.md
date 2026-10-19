# Log

Newest first. Each entry begins with `## [YYYY-MM-DD]` followed by the operation,
so recent activity reads with `grep "^## \[" knowledge/log*.md | head -10`. Past
months move unchanged into `log-archive-YYYY-MM.md` once this file grows past a
screen.

## [2026-10-19] Lint

Full read of all 41 entries (the previous pass had only checked mechanics).
Shape, required fields, status values, `stale_after`, wikilinks, section and
entry indexes, em-dashes and edit-history wording were clean; no new
contradictions found. Added a cross-reference from `jev-decisions-endpoint` to
the new stale-development entry. Still open, each needing a decision: merge or
narrow `compose-reasoning-and-routing-2026-09` and `pipeline-experiments-2026-09`
(same question); `source-yield-2026-09` is a database finding in `evals/`;
compose and TTS cost figures in `structured-output-for-scripts` (8 and 50
cents) disagree with `pipeline-experiments-2026-09` and `episode-208-vs-209`;
`generated-keys-from-a-batch` and `narro-feed-format` are typed `reference`
though they record measured behaviour of a driver and a feed; three `draft`
entries in `references/` dating from 2026-09 are unconfirmed.

## [2026-10-19] Record

`models-and-apis/development-date-extraction-for-stale-news.md`: with
`developmentDate` in the scoring prompt, eligibility dropped 21 stale
developments (guides republished 2026-10-17 about events from Aug and Sep) in
both runs of the episode 296 window, and episode 297 no longer opened with the
auto-mode story. Extraction precision and recall are not measured.

## [2026-10-04] Lint

Mechanical pass over all 39 entries: shape, required fields, status values,
`stale_after`, wikilinks, section and entry indexes, em-dashes and edit-history
wording were clean. The September log moved unchanged to
`log-archive-2026-09.md`. Not done: a contradiction and overlap read of every
entry in full; only the new entry was checked against its neighbours.

## [2026-10-04] Record

`evals/focus-episode-from-external-briefing.md`: how focus episode 285 was
steered from a written briefing. The pipeline does not take the briefing as
input; the feedback recompose did, and removed the eight unrelated window
articles from the script (keyword check only). The articles stay linked, and
pasting markdown as the script is invalid because the format is tagged dialogue.

