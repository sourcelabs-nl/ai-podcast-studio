## Context

The three rules being measured were written to be countable, but they are not countable by string matching. Whether a promise is genuinely deferred depends on what the intervening turns are about; whether a line is a joke is a judgement. So the score needs a model in the loop, and a model in the loop needs to be prevented from doing the parts it is bad at.

## Decisions

**The judge returns anchors, not measurements.**

`ScriptJudge` is asked which turn contains a forward-looking promise, which turn pays it off, which turns contain a humor beat and who speaks them. It is never asked "how many topics apart are these" or "is the humor evenly distributed". Distances, counts and ratios are computed in Kotlin from the returned turn indices.

The reason is reliability, not purity. A model asked to count over a long document gets the count wrong in ways that are invisible in the output; the same model asked to point at a position is checkable, because a human can open the script at that turn and see whether the anchor is right. Anchors make the calibration in the Risks section possible at all.

Alternative considered: ask for a 1-10 engagement rating. Rejected: it cannot be checked, it cannot be attributed to a rule, and it would drift between judge model versions with nothing to notice the drift.

**The deterministic layer is a separate change.**

`script-structure-metrics` computes what can be counted by reading the script and never calls a model. It is free, stable across judge versions and runnable on every generation during an ablation at no cost; the judge runs over the archive once and over the k runs of a variant.

They are split because their properties differ, not only their difficulty. A metric is cheap, reproducible forever and always agrees with the script it describes, so it is recomputed and never stored. A judged score costs money per script, drifts between model versions and therefore has to be versioned and persisted. Binding them together would have made the cheap half wait on the expensive half's migration and calibration.

**Three modes, and the third one is born inert.**

`OFF` makes no call. `ADVISE` judges every generated episode, persists the score and reports it, and never blocks. `ENFORCE` compares the score against a norm and acts on the comparison.

The norm is configuration with no default. Where it should sit is not known: the distribution of judged scores over the archive does not exist yet, and it is that distribution, not a guess, that says what a poor score is. So `ENFORCE` falls back to `ADVISE` while the norm is unset and says so in the log, rather than shipping a plausible-looking number that would reject episodes on no evidence.

This is why all three modes can be built now. The order is fixed by the data, not by the code: build the modes, run in `ADVISE` to produce the baseline, read the distribution, then set a norm and turn `ENFORCE` on.

The mode changes what the run does with the score, never what the judge is asked or what it returns, so rows written under `ADVISE` and under `ENFORCE` are the same kind of row and remain comparable.

**Scores are persisted and versioned.**

A judged score is expensive enough not to recompute and unstable enough across prompt changes to need a version. Each row records the scorer version and the judge model, so a comparison can refuse to mix rows produced by different judges rather than silently averaging them.

**Cost is recorded on the score, not on the episode.**

The episode's own cost columns describe what it cost to produce that episode. Scoring happens afterwards, possibly many times, and adding it there would corrupt both the cost gate and the per-episode economics. The score row carries its own token usage and cost.

## Known constraint: the LLM cache defeats repeated runs

`CachingChatModel` keys on `sha256("<model>:<USER+SYSTEM text>")`. Temperature is not part of the key. So k runs of one prompt variant produce one model call and k-1 cache hits, and the resulting "distribution" is a single sample repeated, with no sign of it in the output.

This never bites in production, where each episode has different articles and a different date, but it is fatal to a deliberate repetition. The loop therefore needs an explicit cache bypass for evaluation runs. A nonce in the prompt would also defeat the cache and is rejected: it alters the text under test.

A second variable has to be held rather than bypassed. `PromptVarietyPicker.pick(podcastId, episodeDate)` rotates opening style, transition vocabulary, sign-off shape, teaser shape and topic entry, deterministically on the date. Within k runs on one date it is constant, which is what we want; across dates it moves independently of the rule being tested. Experiments fix the date, and the selection is recorded so that it can be seen to have been fixed.

## What a run must record

Attribution needs the run's provenance, not a full agent trace: the prompt hash and the variety selection, the model and temperature, whether the response came from the cache, which compose tools fired, and the episode the run produced. A run whose history lookup returned nothing is not comparable with one where it returned something, so tool activity is part of the record.

Full tool-call traces (arguments and results per round) are useful for diagnosing a single strange run and are not needed to attribute a score difference. They are out of scope here and the record is shaped so they could be added later.

## Non-Goals

- Scoring audio. Everything here reads the script.
- An automatic prompt optimiser. The loop is deliberately manual: change one rule, run k times, compare, decide.
- Judging factual accuracy of episodes. This measures structure, not truth.
