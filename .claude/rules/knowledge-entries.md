---
paths:
  - "knowledge/**/*.md"
---

# Knowledge bundle entry rules

`knowledge/` is Open Knowledge Format v0.2: markdown with YAML frontmatter, no loader and no build step. Nothing in the application reads it and no automated process writes to it.

**Frontmatter**: only `type` is required. Types are `finding`, `rule-rationale`, `experiment`, `reference`; a type outside that set is a judgement made in review, not an error.

**Provenance**: `generated` names the actor that produced the current content (`human:<id>` or a model id) and when. `verified` is a separate list of confirmation events, so an unchecked entry stays distinguishable from one a machine confirmed and one a person reviewed. `status` is lifecycle only (`draft`, `stable`, `deprecated`) and never strength of evidence.

**Findings about third-party models** carry `method`, `model_version` and an absolute `stale_after`. Past that instant the entry is a hypothesis to re-measure, not a fact.

**What belongs here**: only knowledge no other store keeps. Git already records what changed and when, the OpenSpec archive why. Link to a commit or an archived change rather than summarising it, and keep entries to what neither can state: what was measured, and what was tried and rejected.

**Never paste raw material.** Content drawn from a session transcript is reformulated into a finished entry. Raw conversation, shell output, file paths from unrelated work and third-party material do not go in: `knowledge/` is tracked in git and therefore permanent.

**Write the current state of knowledge, not a history of the edit.** An entry whose body has started narrating its own revisions gets rewritten.

**Cross-reference with `[[wikilinks]]`**, and list every new entry in its section `index.md`.

`knowledge/log.md` is newest first, each entry beginning `## [YYYY-MM-DD]`, so recent activity reads with `grep "^## \[" knowledge/log.md | head -10`.
