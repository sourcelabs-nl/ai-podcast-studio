---
okf_version: "0.2"
type: rule-rationale
title: The prompt names today and yesterday, and bars assumed times of day
answers: a script says the wrong weekday, greets the listener with "good morning", or the days-and-times rule is being edited
status: draft
source: src/main/kotlin/com/aisummarypodcast/llm/ComposerUtils.kt, buildDaysAndTimesBlock
generated:
  by: claude-sonnet-5-5
  at: 2026-10-22T00:00:00Z
---

The compose prompts used to state only the episode's own date. Everything else
about days was left to the model, which guessed. Episode 303, a Thursday,
opened with "That is not a normal Tuesday feeling" about the day before.

An archive scan over the scripts of 98 episodes showed the two failure shapes
and their frequency. The method was a regex over `script_text`. For weekdays:
every weekday name that is not today or tomorrow, excluding Monday and
sign-off phrasing. For times of day: "good morning/afternoon/evening", "this
morning/afternoon/evening", "tonight", "your morning", "over coffee/breakfast"
and the Dutch equivalents.

- Assumed time of day: 10 of 98 scripts (episodes 217, 219, 223, 241, 258, 268,
  282, 289 and 293, including a "Good morning"). The assumption is wrong for
  anyone who plays the episode at another hour, and it is the common defect.
- Wrong or idiomatic weekday: rare. Episode 303 and an idiom in 282.

The scan counts regex hits, not listener impact, and was not checked against
what listeners heard.

The rule therefore gives the model the one other day it needs (yesterday, the
calendar day before, so Sunday on a Monday), allows a weekday only for today,
yesterday or a day the articles state, and bars weekdays as figures of speech.
The time-of-day ban covers greetings and also when news broke ("this morning"),
since the listener's clock is unknown either way. First-person anecdotes stay
welcome, because forbidding them would remove color the scripts want.

Proposal and reasoning: OpenSpec change `days-and-times-rule`. Related:
[[cliffhangers-must-defer]] for another rule written after a defect found by
reading finished scripts.
