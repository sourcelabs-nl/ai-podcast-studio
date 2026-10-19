---
okf_version: "0.2"
type: finding
title: The scoring model's stated development date catches republished news that the publication date hides
answers: deciding whether to trust developmentDate to drop old stories, a republished or undated-feed guide opens an episode as fresh news, or tuning the stale-development cutoff
status: draft
generated:
  by: claude-sonnet-5-5
  at: 2026-10-19T00:00:00Z
method: >
  Live run on 2026-10-19. The 404 articles in the window of episode 296
  [2026-10-16T14:00Z, 2026-10-19T14:00Z) were rescored with a scoring prompt
  that also asks for developmentDate (the date the content says the development
  happened, YYYY-MM-DD or null, never the publication date). The window was then
  run again as episode 297; the scheduler duplicated it as episode 298, which
  was discarded. Both runs logged the eligibility drop (21 developments) at
  2026-10-19T18:48:47+02:00 and 18:53:05. No hand labels were made.
model_version: deepseek/deepseek-v4.1-flash-1017 (scoring, via OpenRouter)
stale_after: 2027-04-19T00:00:00Z
---

# The scoring model's stated development date catches republished news

Articles whose publication date is recent can describe something that happened
weeks earlier. The scoring prompt now returns `developmentDate`, and the
eligibility stage drops a DEVELOPMENT article whose date is more than one day
before the episode window start. RETROSPECTIVE, unclassified and undated
articles are kept, so a missing date never removes a story. The cutoff and the
rule live in `ArticleEligibilityService` (see the archived OpenSpec change
`openspec/changes/archive/2026-10-19-stale-development-eligibility`). The same
stage already drops evergreen items by `newsType`; see
[[jev-decisions-endpoint]] for how `newsType` agreed with another judge.

## What happened

In both runs the stage dropped 21 developments dated before 2026-10-15. Among
them were Developers Digest guides all published 2026-10-17T00:00Z whose
content described developments from 19 Aug, 11 Sep, 22 Sep and 28 Sep. One of
them, "Claude Code Auto Mode Is Now the Default", had opened episode 296.
Episode 297 no longer opened with the auto-mode story.

One drop is borderline: "StepFun Releases Step 5 Preview Open Weights" has a
development date of 2026-10-14 and a MarkTechPost article dated 16 Oct. It is
two days before the window, so it is dropped by the rule as written, but a
reader could call it recent.

## Not measured

- Precision and recall of `developmentDate` against hand labels. The 21 drops
  were read for plausibility, not checked one by one against the sources.
- How many genuine developments state no date and so pass through unfiltered.

This is one window and one scoring model, so it shows the filter working on a
known case, not how often it is right.

Related: [[episode-208-vs-209]] shows how one episode comparison is easy to
over-read.
