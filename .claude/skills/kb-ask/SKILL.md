---
name: kb-ask
description: Answer a question from the knowledge bundle (knowledge/), citing entries. Use when asked to look up, ask, check or find what we measured, tried, rejected or why a prompt rule exists, and before re-running an experiment or probe that may already have been done. Files back answers with standing value.
---

# Answering from the bundle

## 1. Route

Read `knowledge/index.md`, then the `index.md` of each section that could hold the
answer. Each link carries an `answers` line saying when the entry is needed; open
on that, and open a few too many rather than one too few. If the indexes get
nowhere, grep `knowledge/` for words from the question, including model names and
synonyms.

Only go to the raw layer (episode archive, probe output, git, OpenSpec archive)
when the bundle has nothing, and say that the answer comes from unprocessed
material.

## 2. Answer

Short, with the entry each point came from. Report the state of what you use:

- `status: deprecated`: say it no longer applies and what does.
- `stale_after` passed: say it needs re-measuring, especially for model behaviour.
- `status: draft` or no `verified`: say nobody has confirmed it.
- Two entries contradict: give both and say it needs resolving; don't pick.

If the bundle has no answer, say so. Don't fill the gap from general knowledge
without saying that is what you are doing.

## 3. File back

If producing the answer created something with standing value (a connection
between entries, a contradiction, a conclusion, a new measurement), record it
with `/kb-add` instead of leaving it in the conversation, and log it as
`## [YYYY-MM-DD] File back`. A straight lookup of what an entry already says is
not filed back.
