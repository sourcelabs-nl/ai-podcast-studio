---
okf_version: "0.2"
type: index
title: Project knowledge bundle
status: stable
generated:
  by: claude-opus-5
  at: 2026-09-15T00:00:00Z
---

# Project knowledge bundle

What we have measured about the models and APIs this project depends on, why the
prompt rules are shaped the way they are, and what has been tried and rejected.

This bundle holds only knowledge that no other store keeps. Git records what
changed and when; the OpenSpec archive records why a change was made; the episode
archive, probe output and score rows hold the raw evidence. Entries cite those
rather than restating them.

Nothing in the application reads this directory.

## Sections

- [`tts/`](tts/index.md): measured behaviour of the TTS engine
- [`prompt-rules/`](prompt-rules/index.md): why a composition rule exists
- [`evals/`](evals/index.md): experiments and their conditions
- [`references/`](references/index.md): external material we learn from

[`log.md`](log.md) records what changed here and when, newest first.

## Entry types

| type | holds | required beyond OKF |
|---|---|---|
| `finding` | something measured, usually third-party behaviour | `method`, `model_version`, `stale_after` |
| `rule-rationale` | why a prompt rule exists | `source` naming file and identifier, no copy of the rule text |
| `experiment` | a comparison and its result | what varied, what was held fixed (episode date, variety selection), run count, runs that reached the model, cache bypass |
| `reference` | external material and what transfers from it | what transfers and what does not |

A type outside this set is a judgement made in review, not a validation failure.
A reader meeting an unknown type treats it as a generic entry.

## Provenance

`generated` names the actor that produced the current content, the same field for
a person (`human:<id>`) and for a model. `verified` is a separate list of
confirmation events, so an unchecked entry is distinguishable from one a machine
confirmed and one a person reviewed. An entry is not established merely by
existing here.

`status` is lifecycle only (`draft`, `stable`, `deprecated`), never strength of
evidence. A deprecated entry may be well verified: that is the ordinary case for
a rule removed on evidence.
