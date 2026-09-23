---
okf_version: "0.2"
type: experiment
title: Compose reasoning effort, throughput routing and GPT-6 Luna on one article set
answers: choosing compose's reasoning effort, provider sort or model, or reading an experiment comparison
status: stable
generated:
  by: claude-opus-5-5
  at: 2026-09-23T00:00:00Z
method: >
  Three rounds of the pipeline-runner experiments API against source episode 230
  (The Daily Agentic AI Podcast, 40 articles, interview style), each round one run
  per variant, runs within a round sequential. Sandbox outcome: same article set,
  research included, recap and ScriptJudge run on every script. LLM cache
  bypassed on every run, so all runs reached the model. Compose timeout 5m with
  the per-request timeout fix in place; an earlier set of 18 runs made under the
  Spring AI 2.0.1 60-second cap is discarded. Figures read from the comparison
  endpoint GET .../episodes/230/experiments on 2026-09-23.
model_version: deepseek/deepseek-v4.1-flash (served by Novita), openai/gpt-6-luna (served by OpenAI), 2026-09-23
stale_after: 2026-12-23T00:00:00Z
---

# Compose reasoning effort, throughput routing and GPT-6 Luna on one article set

**Varied:** compose reasoning effort (none, low, medium as the baseline, high),
OpenRouter `provider.sort = throughput`, and the compose model (GPT-6 Luna at
medium). **Held fixed:** the article set of episode 230, the podcast's prompt and
settings, every stage other than compose, the judge. Three runs per variant; the
baseline has two completed runs at the time of writing.

| Variant | Judge per run | Mean | Compose cost | Episode cost | Compose time | Reasoning tokens | Words |
|---|---|---|---|---|---|---|---|
| baseline, medium | 0.69, 0.88 | 0.79 | 1.91¢ | 5.00¢ | 65s | 11.7k | 2,152 |
| effort none | 0.94, 0.77, 0.69 | 0.80 | 1.09¢ | 4.19¢ | 17s | 0 | 1,955 |
| effort low | 0.77, 0.94, 0.79 | 0.84 | 2.50¢ | 5.60¢ | 76s | 13.7k | 2,145 |
| effort high | 0.69, 0.79, 0.86 | 0.78 | 4.74¢ | 7.83¢ | 161s | 35.6k | 1,991 |
| throughput sort | 0.61, 0.78, 0.70 | 0.70 | 2.22¢ | 5.34¢ | 78s | 14.8k | 2,005 |
| GPT-6 Luna, medium | 0.50, 0.67, 0.50 | 0.56 | 0.46¢ | 3.54¢ | 47s | 1.4k | 2,245 |

## What the result supports

- **Reasoning does not buy judged quality in compose.** None, low, medium and high
  overlap completely: every DeepSeek variant spans roughly 0.69 to 0.94 across its
  own runs, wider than any gap between variant means. Reasoning does buy cost and
  time, monotonically: effort none composes in about a quarter of medium's time at
  57% of its compose cost, and high costs 2.5x medium.
- **Throughput sort changed nothing it was meant to change.** Every DeepSeek run,
  sorted or not, was served by Novita, because the quantization floor leaves few
  eligible endpoints and the sort reorders only those. It was not faster (78s
  against 65s).
- **GPT-6 Luna scores lower.** All three runs (0.50 to 0.67) sit at or below the
  lowest DeepSeek run, the one gap in this table larger than the run-to-run spread.
  It is four times cheaper in compose, but compose is under half of an episode's
  cost, so the episode saves about 1.5¢.

## A second article set: none against medium

Episode 226 (a different day's 40 articles, same podcast), three runs each, cache
bypassed, compose timeout 5m, served by DeepInfra this time:

| Variant | Judge per run | Mean | Compose cost | Episode cost | Compose time | Words |
|---|---|---|---|---|---|---|
| baseline, medium | 0.86, 0.69, 0.67 | 0.74 | 0.82¢ | 3.94¢ | 187-281s | 2,238 |
| effort none | 0.56, 0.86, 0.42 | 0.61 | 0.34¢ | 3.47¢ | 47-79s | 2,275 |

On this set effort none scored lower, with the lowest run of either experiment
(0.42). Pooled over both sets, medium averages 0.76 over five runs and none 0.71
over six. The difference is still inside the run-to-run spread, but it no longer
points the way the first set did, so the evidence does not support moving the
compose default off medium. The cost and time saving of none holds on both sets
(about 60% of compose cost and a quarter to a third of the time). Medium's 281s
run is why the compose timeout is now 10 minutes.

## What it does not support

Three runs on one article set of one interview-style podcast. The judge measures
the attention devices ScriptJudge counts, not prose quality or factual accuracy, so
"reasoning does not help" means it does not move that score. Effort none's scripts
ran about 200 words shorter on the first set, within the run-to-run spread.

Related: [[compose-reasoning-and-routing-2026-09]] measured the reasoning share and
routing on episodes 220-228; [[evaluation-run-cache-bypass]] explains why every run
bypassed the cache; [[spring-ai-2-0-1-per-request-timeout]] why the first 18 runs
were discarded; [[judged-baseline-2026-09]] gives the archive's score spread; [[openrouter-routing]]
why the throughput sort could not change the serving provider.
