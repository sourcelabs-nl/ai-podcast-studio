## Why

Every prompt decision of the past week rests on listening to one episode. The comparison of 208 against 209 looked convincing and establishes nothing: different articles, one run, the LLM cache not bypassed, the variety selection not held. The same is now true of the backchannels added today.

A judged score would settle those questions, but not all of them need a judge. A large part of what the prompt asks for is structure, and structure is countable by reading the script: how long the expert speaks before handing over, whether the interviewer is the only one being funny, whether a backchannel is a short interviewer turn sitting between two expert turns, how much an episode's length varies from day to day.

That layer is free, deterministic, stable across judge versions and available over the whole archive today. It is worth having on its own, before deciding whether the judge is worth its build.

The first questions waiting for it are concrete. Episodes over the past two weeks ran from 1676 to 2971 words, 11 to 19 minutes, at identical settings, which is a wider spread than a daily show should have. And from tomorrow, whether the model actually writes backchannels is a structural fact, not a matter of opinion.

## What Changes

A new `com.aisummarypodcast.eval` package computes, with no model call:

- `ScriptMetrics`: turn count, turns and words per role, the median, maximum and over-threshold count of expert turn lengths, word share per role, and which role owns each `[laugh]` tag.
- Backchannel detection: a short interviewer turn between two expert turns, which is the shape the prompt now asks for and the only device whose presence can be established without a model.
- Adjacency reporting: consecutive same-speaker turns, counted and located, since these are legitimate after a backchannel and a defect otherwise.

Metrics are computed on demand and not persisted: the computation is free and the script is already stored, so a table would only create a second copy that can fall out of date.

A REST endpoint returns the metrics for one episode and for a range of episodes, so the archive is read through the API rather than by querying the database.

## Capabilities

### New Capabilities

- `script-structure-metrics`: countable structural facts about an episode script, with no model in the loop.

### Modified Capabilities

None. Episode generation is untouched; metrics read scripts that already exist.

## Impact

- Backend: new `eval` package (`ScriptMetrics`, its result type, a controller and DTOs).
- No database change, no LLM call, no cost, no new pipeline stage.
- No change to composition, TTS, publishing, or the frontend.

## Risks

- **`[laugh]` ownership is a proxy, not a humor count.** A joke carrying no laugh tag is invisible here. It is reported as what it is; counting humor needs the judge.
- **Backchannel detection is shape-based.** A short interviewer turn between two expert turns is what a backchannel looks like, but a genuinely short question has the same shape. The metric reports candidates, and a claim that the device is working is checked by reading a few of them.
