## Why

What this project knows about its own output lives in three places that cannot be consulted. Prompt rules carry their rationale in KDoc beside the code that implements them, findable only by someone already reading that file. Measurements made against the Inworld API (that `[measured and clear]` costs 27% in pace, that a phoneme span under `CREATIVE` mangles a name three times in eight) exist only in a chat transcript. And a rule that was removed leaves no trace of why, so the next session is free to reintroduce it.

The cost is already paid, repeatedly. Episode 194 shipped 25 seconds of `[deadpan]`, episode 207 shipped three plural sound tags, episode 208 shipped a pace cue mid-episode. Each was diagnosed from scratch, and two of the three were re-derived by running paid probes against an API that had already answered the question.

Scoring makes this sharper. A scoring loop produces numbers that justify changing prompt rules, and a number separated from its measurement conditions is an invitation to act on noise. An ablation that finds nothing is the cheapest result to lose and the most expensive to re-derive.

The reason knowledge bases like this normally fail is not the writing, it is the upkeep: cross-references, superseded claims, summaries that quietly go out of date. That burden grows faster than the value and a person abandons it. It is also the part a model does at near zero cost, which is what makes the pattern worth adopting now rather than earlier.

## What Changes

A knowledge bundle at `knowledge/`, tracked in git, in Open Knowledge Format v0.2: markdown with YAML frontmatter, readable by a person and by an agent with no loader, no database and no runtime code.

Three layers, after the LLM-wiki pattern:

- **Raw sources** stay where they are and are never rewritten by this: the episode archive and its scripts in SQLite, probe output, the reference-show transcripts.
- **The bundle** is written and maintained by the agent. Entries under `tts/`, `prompt-rules/`, `evals/` and `references/`, with `index.md` per directory for progressive disclosure and a `log.md` recording what happened when.
- **The schema** is a section of `CLAUDE.md` describing the conventions and the operations, so a session behaves as a maintainer of the bundle rather than rediscovering it. It is expected to be revised as we learn what works.

Three operations:

- **Record.** A measurement, a removed rule, or an experiment result is written into the bundle as part of the task that produced it, touching the entry, the affected index, and the log.
- **Query.** A question is answered from the bundle, and an answer worth keeping is filed back as an entry rather than left in the transcript.
- **Lint.** A periodic pass over the bundle for contradictions between entries, claims past their `stale_after`, entries nothing links to, and missing cross-references.

Provenance rides in the frontmatter rather than in who holds the pen. `generated` records the actor and time for every entry, human or model alike; `verified` is a separate list of confirmation events, so an entry the agent wrote and nobody has checked is visibly distinct from one a person confirmed.

## Capabilities

### New Capabilities

- `knowledge-bundle`: durable, auditable project knowledge about what makes an episode work, maintained by the agent.

### Modified Capabilities

None.

## Impact

- New top-level `knowledge/` directory in git.
- A new section in `CLAUDE.md`, which is the configuration that makes the rest happen.
- No runtime code, no database change, no dependency, no build step. Nothing in the application reads the bundle.

## Risks

- **A stale finding is worse than no finding**, carrying the authority of a measurement while describing behaviour a vendor has since changed. Findings about third-party behaviour pin the model version and carry `stale_after`, and lint is what makes that field do anything; without a lint pass an expiry date is a field nobody reads.
- **The agent writes most of it, so an unchecked claim can look settled.** This is what `verified` is for. Nothing is presented as established because it is written down; it is established when a confirmation event says who checked it.
- **The bundle can drift from the code it explains.** A rationale entry names the file and identifier it describes and does not quote the rule text, which would guarantee two copies of one rule with nothing keeping them equal.
- **Volume without curation is its own failure.** Lint exists to remove and merge, not only to add. An entry is edited to state what is currently known rather than accumulating a changelog in its body; the chronology belongs in `log.md`.
