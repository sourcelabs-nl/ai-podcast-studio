---
paths:
  - "knowledge/**/*.md"
---

# Knowledge bundle entry rules

`knowledge/` is Open Knowledge Format v0.2: markdown with YAML frontmatter, no loader and no build step. Nothing in the application reads it and no automated process writes to it.

**The format is specified in `knowledge/index.md`**: the frontmatter fields, the entry types, what provenance means and how `status` is used. That file travels with the bundle, so it is the one source and this file does not restate it. What follows is how an entry gets written here.

**`answers` states the occasion, not the conclusion.** The title already says what was found; `answers` says when someone would need it, because that is what the section index lists and what a reader routes on. "Read when a name is mispronounced" finds its entry; "unreliable phoneme spans" only finds it if you already knew.

**What belongs here**: only knowledge no other store keeps. Git already records what changed and when, the OpenSpec archive why. Link to a commit or an archived change rather than summarising it, and keep entries to what neither can state: what was measured, and what was tried and rejected.

**Never paste raw material.** Content drawn from a session transcript is reformulated into a finished entry. Raw conversation, shell output, file paths from unrelated work and third-party material do not go in: `knowledge/` is tracked in git and therefore permanent.

**Write the current state of knowledge, not a history of the edit.** An entry whose body has started narrating its own revisions gets rewritten.

**Cross-reference with `[[wikilinks]]`**, and list every new entry in its section `index.md`.

`knowledge/log.md` is newest first, each entry beginning `## [YYYY-MM-DD]`, so recent activity reads with `grep "^## \[" knowledge/log.md | head -10`. When it grows past what one screen can hold, past months move unchanged into `knowledge/log-archive-YYYY-MM.md` and `log.md` keeps the current one; the full history stays readable with `grep "^## \[" knowledge/log*.md`.
