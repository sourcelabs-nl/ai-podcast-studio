---
name: kb-tidy
description: Lint and tidy the whole knowledge bundle (knowledge/). Use when asked to lint, tidy, clean up, check or review the knowledge bundle, or at the end of any session that touched knowledge/ (the Lint operation). Finds contradictions, expired stale_after, orphans, missing entries and cross-references, and entries narrating their own edit history.
---

# Linting the bundle

Cover every entry, not only the ones this session changed: a new entry is the most
common way an old one becomes wrong. Read `knowledge/index.md`, every section
`index.md`, and every entry in full.

## Check

- **Shape.** Missing `okf_version`, `type`, `title`, `answers` or `status`; a
  `status` outside `draft`/`stable`/`deprecated`; a `finding` without `method`,
  `model_version` or an absolute `stale_after`; an `experiment` without its
  conditions; an `answers` line stating a conclusion instead of an occasion.
- **Stale.** A `stale_after` that has passed. A `draft` that has sat unconfirmed.
- **Orphans and dangling links.** An entry not listed in its section index, a
  section not listed in `knowledge/index.md`, a `[[wikilink]]` to no entry, an
  index line that no longer matches the entry's `answers`.
- **Gaps.** A concept referenced in several entries with no entry of its own;
  entries on the same subject that do not cross-reference each other.
- **Contradictions and overlap.** Two entries saying different things, or
  answering the same occasion. You find these by reading, not by pattern; they are
  the most valuable result.
- **Prose.** A body narrating its own revisions ("updated to", "previously this
  said"), em-dashes, pasted raw material (transcript, shell output).
- **Log.** `knowledge/log.md` past a screen: move past months unchanged into
  `log-archive-YYYY-MM.md`.

## Fix

Lint removes and merges as well as adds. Fix shape, indexes, links and prose
directly. For contradictions, merges, deprecations and re-measuring an expired
finding, propose the change and wait for approval, since those change what the
bundle claims. A merged-away entry becomes `status: deprecated` with one line
pointing at the survivor.

Add a `## [YYYY-MM-DD] Lint` entry to `knowledge/log.md` saying what was fixed and
what is left open. If nothing was found, say so in one line and log nothing.
