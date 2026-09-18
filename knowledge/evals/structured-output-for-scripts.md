---
okf_version: "0.2"
type: experiment
title: Structured output fixes the speaker tags and breaks the length
status: stable
method: >
  Three paired runs against the OpenRouter chat-completions API directly, not
  through the application, so nothing touched the LLM cache and each run is an
  independent sample. Both arms sent the same prompt, built from episode 222's
  own forty article summaries, asking for roughly 1800 words as an interview of
  45-60 alternating turns. The arms differ only in the response format: one asks
  for the project's `<interviewer>`/`<expert>` tagged free text, the other sets
  `response_format` to a strict JSON schema of `{turns: [{role, text}]}` with
  `additionalProperties: false`. Temperature 0.95 (the podcast's configured
  value), max_tokens 16000, reasoning disabled, on both arms.
varied: the response format
held_fixed: prompt, article set, model, temperature, max_tokens, reasoning setting
runs: 3 per arm
runs_reaching_model: 6
cache_bypassed: true (the application cache was never involved)
model_version: deepseek/deepseek-v4.1-flash
stale_after: 2026-12-17T00:00:00Z
generated:
  by: claude-opus-5
  at: 2026-09-17T00:00:00Z
---

Whether the compose stage should return JSON turns instead of tagged free text.
The reason to ask is [[../prompt-rules/index]]'s repeated speaker-tag repairs and
episode 222, whose second half was attributed to the wrong speaker.

**Free text produced malformed speaker tags in every single run.** Not once in
three, but three times out of three, and always the same fault: an `<expert>`
turn closed with `</interviewer>`.

| run | `<expert>` | `</expert>` | `<interviewer>` | `</interviewer>` | mismatches |
|---|---|---|---|---|---|
| 0 | 37 | 34 | 37 | 40 | 3 |
| 1 | 34 | 29 | 35 | 40 | 5 |
| 2 | 32 | 30 | 33 | 35 | 2 |

This is the behaviour of the model, not an accident of one episode. Episode 222
was simply the first run where enough of them landed to be audible.

**Structured output eliminated it.** Three runs out of three parsed as valid
JSON, with `finish_reason: stop`, strictly alternating roles, and no role outside
the two allowed. Nothing was truncated.

**The truncation fear was wrong.** The concern that a 1800-word creative response
would not survive a JSON envelope, argued from the dedup stage's history of
truncated responses, did not reproduce at all: the longest structured run emitted
8099 output tokens against a 16000 budget and still stopped normally. Dedup's
failures came from a budget sized too small for its input, not from the response
format.

**Structured output loses control of length, which is the expensive axis.**
Against a 1500-word target:

| arm | words per run | output tokens | seconds |
|---|---|---|---|
| free text | 1950, 2103, 2114 | 3061-3363 | 12.6, 15.0, 97.2 |
| structured | 2241, 3862, 5131 | 4193-8099 | 161.4, 193.1, 449.2 |

Free text overshoots by a consistent third. Structured overshoots by between a
half and two and a half times, and unpredictably.

That matters because of what the script costs downstream rather than what it
costs to generate. Compose runs about 8 cents an episode; TTS runs about 50, at
roughly 2.5 cents per thousand characters. The longest structured run's 39482
characters would be about 98 cents of speech and around forty minutes of audio,
against episode 222's 50 cents and twenty-one minutes. A response-format change
that doubles script length costs more than the whole LLM budget it sits in.

Latency is not a blocker: the worst structured run took 449s against a 20-minute
compose timeout, inside the 1m03s-18m11s range compose already spans.

**What follows.** Structured output is worth adopting on correctness grounds, and
the objection recorded against it does not survive measurement. It is not a
drop-in: length control has to be solved first, and this experiment does not
establish whether that is a prompt problem or a property of the format. Until
then the repair in `ComposerUtils.repairMismatchedTurnClosers` is not a
belt-and-braces measure but load-bearing, because the model emits the fault it
corrects on every run.
