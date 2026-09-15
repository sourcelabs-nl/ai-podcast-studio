## Why

Three engagement rules were rewritten after episode 208 was audited by hand: cliffhangers must defer their payoff, humor must come from more than one speaker, and the introduction teaser must name several topics. A fourth was added since: the interviewer backchannels while the expert holds the floor. Whether any of them changes anything is not known, and cannot be established the way the audit was done.

The composer is not deterministic. Two runs over the same articles produce different scripts, so a single regeneration that happens to contain a cliffhanger says nothing about the prompt. Attribution needs a distribution, which needs a score that can be computed the same way many times.

`script-structure-metrics` covers what can be counted by reading the script. What is left is what cannot: whether a promise is genuinely deferred depends on what the intervening turns are about, and whether a line is a joke is a judgement. That part needs a model in the loop, and a model in the loop needs to be prevented from doing the parts it is bad at.

Two facts make it affordable. The database already holds 166 episodes with full script text, so a baseline costs one judge call per episode and is a distribution rather than a single point. And a compose run averages 10.3 cents, so five runs of a prompt variant costs about fifty cents, which makes ablation affordable: remove one rule, run five, compare.

There is a specific question waiting for it. `CURIOSITY HOOKS` asks for a teaser hook before transitions while `STRATEGIC CLIFFHANGERS` asks for deferral. Those are opposing demands on the same position in the script, and the cheap way to satisfy both is a hook immediately before the story it announces, which is exactly the failure episode 208 showed. Whether that bullet suppresses cliffhangers is testable by ablation and guesswork otherwise.

## What Changes

The judged layer, on top of the deterministic metrics delivered by `script-structure-metrics`.

- `ScriptJudge` makes one LLM call per script and returns **anchors, not verdicts**: the turn index of each forward-looking promise and of its payoff, the turn index and role of each humor beat and whether it reacts to the previous turn, and the topics named in the introduction teaser. Deferral distance, speaker balance and counts are then computed from those indices in Kotlin.
- `EpisodeScoringService` scores a range of episodes and persists the result, so the archive is judged once rather than on every read.
- A new `episode_scores` table, written by a Flyway migration, holds one row per episode per scorer version.
- A REST endpoint triggers scoring over a set of episodes and reads the results back, so the database is never queried directly for them.
- The judge runs under one of three modes (`OFF`, `ADVISE`, `ENFORCE`). `ENFORCE` compares the score against a norm that is configuration with no default, and falls back to `ADVISE` while that norm is unset, so the mode can be built before the baseline that will supply the norm exists.
- An explicit cache bypass for evaluation runs, without which k repetitions of a prompt variant are one model call and k-1 cache hits.

The judge is a new pipeline stage (`EVAL`) with its own model default and pricing, so it follows the per-stage model resolution every other LLM feature uses and its cost is tracked separately from the episode's own.

## Capabilities

### New Capabilities

- `script-attention-scoring`: a repeatable judged score for the attention devices a script is supposed to contain.

### Modified Capabilities

None. Episode generation is untouched; scoring reads scripts that already exist.

## Impact

- Backend: `ScriptJudge`, `ScriptScore`, `EpisodeScoringService`, a controller and DTOs in the existing `eval` package, `PipelineStage.EVAL`, a `StageDefaults.eval` entry and its pricing in `application.yaml`.
- Database: new `episode_scores` table via Flyway.
- Cost: scoring the 166-episode archive is one judge call per episode on a cheap model.
- Depends on `script-structure-metrics` for the deterministic layer and its `eval` package.
- No change to composition, TTS, publishing, or the frontend.

## Risks

- **The LLM cache silently defeats repeated runs.** `CachingChatModel` keys on model plus prompt text and ignores temperature, so k repetitions of one variant would return one script k times and report a distribution with no spread. The loop needs an explicit cache bypass; this was found by reading the cache key, not by observing a wrong result, and would have invalidated every comparison had it not been.
- **The scorer can be wrong, and a wrong scorer is worse than none**, because it would justify deleting prompt rules that work. Before any rule is changed on the strength of a number, the judge's anchors are checked by hand against a handful of episodes whose quality is already known, and that calibration is recorded.
- **A silently substituted judge model is invisible in the output.** The score row records the judge model for exactly this reason, and a comparison refuses to mix rows produced by different judges.
- Scorer output is versioned so a change to the judge prompt does not silently invalidate comparisons against earlier rows.
