---
okf_version: "0.2"
type: experiment
title: A focus episode steered by an external briefing, episode 285
answers: producing a focus episode from a written briefing or an outside script, or finding unrelated window articles in a focus episode's script
status: draft
varied: the reviewer feedback on the recompose (none against the full briefing plus an instruction to ignore the selected articles)
held_fixed: the podcast, the article window, the focus text, the cached research results
runs: 2
runs_reaching_model: 2
cache_bypassed: false
generated:
  by: claude-sonnet-5-5
  at: 2026-10-04T00:00:00Z
---

Episode 285 was a focus episode, "The Bottleneck Was Never the Code", made from
a briefing written outside the pipeline (`docs/special-episodes/the-bottleneck-was-never-the-code.md`).
This is one episode and one comparison, so it shows what happened, not how often.

**What a focus episode takes in.** `POST /generate` with a `focus` body returns
202 and an episode id. The pipeline then scores every article in the current
window against the focus text and links the ones that clear the threshold, and
it runs its own web searches. The briefing is not an input. The first compose
came back in `PENDING_REVIEW` at 1702 words with eight linked articles on the
Articles tab, none about the subject (an Airbnb rebuild, Copilot controlling
desktop apps, an agent-memory post, a desktop GPU, a database acquisition, two
reply posts). At least one reached the script: it promised "agents that finally
remember your name", which the briefing does not contain.

**What worked.** `POST /episodes/{id}/regenerate-script` takes a `feedback`
string, and research reruns from cache. The feedback carried the full briefing,
an instruction to ignore the selected articles by name, a target of about 21
minutes spoken (roughly 3000 words) and a request for spoken style with no
tables or headings. The recompose reached the model and returned 3406 words. A
keyword search for the unrelated topics found none, the briefing's gated-release
material (Fairwind, Astra) was present, the opening and closing both frame it
as a special and extra deep dive, and no markdown came through. The length ran
about 13% over the requested target (compare [[length-is-not-a-defect]]).

**What did not work, or was not tried.**
- The linked articles stay linked. A focus episode keeps them for traceability,
  and no endpoint to unlink them turned up. The list was last read before the
  recompose and not read again after it.
- Pasting the briefing as the script through `PUT /episodes/{id}/script` was
  not tried, deliberately. The script format is tagged dialogue
  (`<interviewer>` and `<expert>`), so headings, tables and links would have
  been read out by the TTS. Only tagged dialogue text would be a valid paste.
- Whether the instruction to ignore the articles or the briefing itself removed
  the unrelated content is not separated: there was no run with only one of
  them. The keyword search shows absence, not that the articles had no
  influence on phrasing.
- The recompose was not bypassed from the cache, and the script was read by
  keyword search only, not listened to. Hence `status: draft`.

The evaluation conditions that make runs independent are in
[[evaluation-run-cache-bypass]]. The focus episode flow is described in the
README under Focus Episodes.
