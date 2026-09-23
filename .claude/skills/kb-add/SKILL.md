---
name: kb-add
description: Record a finding, experiment result, rule rationale or reference in the knowledge bundle (knowledge/). Use when asked to record, add, write down, file or save something we measured, tried, rejected or learned, or at the end of a task that produced such knowledge (the Record operation).
---

# Recording an entry

The format is specified in `knowledge/index.md` and the writing rules in
`.claude/rules/knowledge-entries.md`. Read both first; this skill is the procedure,
not a restatement.

## 1. Update before adding

Read `knowledge/index.md` and every section `index.md` that could hold the subject.
An entry that already covers it gets updated, not joined by a second one. If the new
knowledge contradicts an entry, say so explicitly: that is the most important
thing found. A rule removed on evidence goes to `status: deprecated` with one line
on what holds instead; it is not deleted.

Check the knowledge is not already kept elsewhere. What changed and when is git;
why is the OpenSpec archive. Link to those instead of restating them.

## 2. Write the entry

- Pick the section (`tts/`, `prompt-rules/`, `evals/`, `references/`) and a
  kebab-case slug.
- Frontmatter: `okf_version: "0.2"`, `type`, `title`, `answers`, `status`,
  `generated` (yourself and now). Leave `verified` untouched on an update: a
  confirmation of the previous content says nothing about this one.
- Add what the type requires (see the table in `knowledge/index.md`): a `finding`
  needs `method`, `model_version` and an absolute `stale_after`; an `experiment`
  states what varied, what was held fixed, run count, runs that reached the model
  and cache bypass; a `rule-rationale` names its `source`; a `reference` says what
  transfers and what does not.
- `answers` names the occasion ("read when ..."), not the conclusion.
- A result showing no difference is recorded on the same terms.
- Cite raw material (episode ids, probe output, commits, archived changes); never
  paste it. Cross-reference related entries with `[[wikilinks]]`.
- Use `status: draft` unless the content was checked.

## 3. Index and log

- Add the entry to its section `index.md`, with its `answers` line under the link.
- Add a `## [YYYY-MM-DD] Record` entry at the top of `knowledge/log.md`: the file
  and one or two sentences on what is now known.

Finish by running `/kb-tidy`, since a new entry is the most common way an old one
becomes wrong.
