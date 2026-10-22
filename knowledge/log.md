# Log

Newest first. Each entry begins with `## [YYYY-MM-DD]` followed by the operation,
so recent activity reads with `grep "^## \[" knowledge/log*.md | head -10`. Past
months move unchanged into `log-archive-YYYY-MM.md` once this file grows past a
screen.

## [2026-10-22] Lint

Covered every entry. Shape, required fields, status values, `stale_after` (earliest 2026-12-15), section indexes, wikilinks, em-dashes and edit-history wording were clean, including the new `prompt-rules/days-and-times.md`, which overlaps no existing entry. Open: the only dangling wikilink is in the unchanged `log-archive-2026-09.md`; `evals/` still nears the size at which a split is worth proposing.

## [2026-10-22] Record

`prompt-rules/days-and-times.md`: why the compose prompts name today's and yesterday's weekday and bar assumed times of day. A scan of 98 archived scripts found 10 assuming a time of day and wrong-weekday use only in 303 and 282.

## [2026-10-21] Lint

Covered every entry. `prompt-rules/backchannels.md` said two or three
backchannels an episode while the interview prompt now asks for at most two:
corrected, and linked to `evals/compose-stakes-first-episode-302.md`.
`models-and-apis/jev-decisions-endpoint.md` now says its 120-article sample was
taken at the then threshold of 5, since the podcast's threshold is 7. No expired
`stale_after`, orphans, section index mismatches, em-dashes or edit-history
narration found. Open: `evals/` holds 14 entries and nears the size at which a
split is worth proposing; the only dangling wikilink is in the unchanged
`log-archive-2026-09.md`.

## [2026-10-21] Record

`evals/compose-stakes-first-episode-302.md`: episode 302, the first composed
with the stakes-first rules after rescoring with the rubric and a threshold of 7,
used 21 articles and no arXiv papers (episode 301: 35 articles, 14 papers) and
showed so-what questions, stakes before mechanism and a thematic rapid-fire. One
episode, no judged score, listener verdict pending. Also records that renaming
subtopics sends already-scored articles into "Other", which cost episode 301 14
of its 35 articles.

## [2026-10-21] Record

`evals/newsworthiness-rubric-episode-299.md`: rescoring episode 299's 70
candidates with a listener-interest rubric cut papers at 7+ from 23 to 6, but
total volume at 7+ only fell from 36 to 20, since 22 to 27 candidates sit at 6.
Volume falls only with the relevance threshold at 7. No script-quality
measurement yet.

## [2026-10-20] Record

`references/decision-model-and-eu-hosting-alternatives.md`: HostYourAI also
runs a Jev-SDK-compatible EU decision API; only Loes Beslis (16K context,
€0.15 per 1M) can carry the dedup gate's shared state. Repriced at HostYourAI
rates, episode 297 would have cost about €1.42 instead of $0.55, almost all of
it in scoring.

## [2026-10-20] Lint

Checked the new `references/decision-model-and-eu-hosting-alternatives.md`
against the bundle: shape, `stale_after`, wikilinks, section index line and
em-dashes are clean, and its figures (1,676 median input tokens, about 93
candidates per chunk, the 2026-09-21 outage, one provider for Jev) agree with
`jev-decisions-endpoint` and `openrouter-routing`. Both already link to it or
are linked from it; no contradictions found. The open items from the 2026-10-19
lint are unchanged. The OpenRouter EU 403 on this account is not in the bundle,
so the EU-hosting section cannot reference it.

## [2026-10-20] Record

`references/decision-model-and-eu-hosting-alternatives.md`: Clef and Clef-flash
are callable on OpenRouter's decisions endpoint with Jev's request shape and
more than one provider, but cap a request at 64 questions; HostYourAI serves
DeepSeek V4.1 Flash in the EU at several times the current scoring cost. Desk
research only; nothing was called.

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

