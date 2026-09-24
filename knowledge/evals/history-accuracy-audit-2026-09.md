---
okf_version: "0.2"
type: finding
title: Scripts rarely repeat covered stories, and often ignore the FOLLOW-UP header they were given
answers: considering a richer episode-history store to improve scripts, or asking why a continuation story aired as fresh news
status: draft
method: >
  Read-only SQL over the main podcast's episodes 195 to 230 (2026-09-02 to
  2026-09-23). Pass one compared each of the 8 episodes with a full 7-episode
  lookback (209, 220, 221, 223, 224, 225, 226, 230) against its window on topic
  labels, recaps and script text, counting re-announced stories, wrong claims of
  prior coverage, repeated statistics and topics returning from outside the
  window. Pass two listed the rows of episode_articles carrying a
  follow_up_context and read the matching script passage for a sample of them.
  Matching was by keyword and phrase, so paraphrased repeats and paraphrased
  acknowledgements are under-counted. No ScriptJudge criterion covers history,
  so there was no automated score to compare against.
model_version: the compose and dedup models configured for episodes 195 to 230
stale_after: 2027-03-24T00:00:00Z
generated:
  by: claude-opus-5-5
  at: 2026-09-24T00:00:00Z
---

**Coverage decisions hold.** Across the 8 episodes with a full lookback, no story
was re-announced as new, no script claimed prior coverage that did not happen, and
no statistic was found repeated verbatim. One topic returned from outside the
7-episode window (Agent Skills in Genkit Go, episode 201, then 225), seen in the
recap and not confirmed in the script. Episodes 208 and 209 share a date and
most topics because 209 is a regeneration of the same day, see
[[episode-208-vs-209]], not a repeat on air.

**Follow-up context reaches the prompt and often stops there.** The
`previousContext` the clustering call writes is stored per article in
`episode_articles.follow_up_context` and rendered as a `[FOLLOW-UP: ...]` header in
the compose prompt. Around 90 such rows exist across episodes 195 to 230. Of the
three checked in the script, one was acknowledged (episode 201: "We covered Astra
on this show before") and two were narrated as fresh news (episode 220, Google's
zero-trust agent guide; episode 229, Meta Muse). None of the 8 scripts in the
first pass contains any phrase acknowledging earlier coverage.

The compose rule that consumes the header, `WHAT COUNTS AS NEW` in
`ComposerUtils.kt`, says the model *may* reference prior coverage. It permits the
acknowledgement and never asks for it, which matches what the scripts do.

**Not established.** Three spot checks are not an acknowledgement rate. Identical
follow-up texts recur across consecutive episodes from 208 onward, which may mean
the same continuation is matched repeatedly rather than judged afresh; that was
seen and not investigated.

**What this means for a history store.** The failures a more precise history
would prevent (re-announcement, false "previously" claims, a window too short)
were absent or single weak cases in this sample. The observed gap lies after the
history, in how the composer uses a follow-up it was already given.

Related: [[dedup-guards-articles-not-claims]], [[jev-decisions-endpoint]], [[judged-baseline-2026-09]]
