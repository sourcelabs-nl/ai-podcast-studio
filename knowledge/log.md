# Log

Newest first. Each entry begins with `## [YYYY-MM-DD]` followed by the operation,
so recent activity reads with `grep "^## \[" knowledge/log*.md | head -10`. Past
months move unchanged into `log-archive-YYYY-MM.md` once this file grows past a
screen.

## [2026-09-23] Record

Added `evals/pipeline-experiments-2026-09.md`: on episode 230's article set, compose
reasoning effort none/low/medium/high scored within run-to-run spread while cost
and time rose with effort; throughput sort still landed on Novita and was not
faster; GPT-6 Luna scored below every DeepSeek run.

## [2026-09-23] Record

Added `references/spring-ai-2-0-1-per-request-timeout.md`: the 2.0.1 upgrade sends
the chat options' timeout per request, defaulting to 60s over the client's stage
timeout, which cut every long compose request off after a minute.

## [2026-09-23] Record

Added `references/openai-reasoning-models-reject-temperature.md`: gpt-6-luna does
not accept `temperature`, and with `require_parameters` OpenRouter reports that as
a 404 "No endpoints found", indistinguishable from the quantization floor's 404.

## [2026-09-23] Record

Added `references/mutation-testing-ai-written-tests.md` as a draft: what the Augment
Code guide on mutation testing AI-generated code claims, and what applying PIT here
would take (Kotlin compiler-generated mutants, the Kotlin tool landscape, a scoped
first trial on PIT 1.30.0, which supports Java 25). No trial has run.

## [2026-09-22] Record

Added `evals/compose-reasoning-and-routing-2026-09.md`: on episodes 220-228,
DeepSeek compose spent 67-90% of its output tokens on reasoning, default
price-weighted routing served it from a slower DeepInfra endpoint than faster
endpoints passing the same quantization floor, and moving research ahead of
compose (precompose-research, commit cb9e14b) removed a second full-price
tool-calling round trip. Recorded as unresolved whether throughput routing or
lower reasoning effort holds up under an A/B on script quality.

## [2026-09-22] Record

Added `references/generated-keys-from-a-batch.md`: the xerial SQLite driver
returns no generated keys from an `executeBatch`, which is what made every
Spring Data JDBC `saveAll` of new aggregates fail with "After saving the
identifier must not be null" and left `episode_candidate_articles` empty from
the day it shipped. Measured with two JDBC programs rather than argued from the
specification, after an explanation of the driver's behaviour turned out to be
worth less than one probe of it. The row-by-row alternative was timed in the
same pass: 200 inserts in 7.5 ms, so batching buys nothing here.

## [2026-09-22] Lint

Gave every entry an `answers` line and put it under each link in the four section
indexes, so an index says when you would need an entry rather than only what it
concluded. The titles here are already statements, which does half that work, but
a title states the conclusion and a reader arrives with the occasion: "a name is
mispronounced" reaches `phoneme-spans-under-creative`, "phoneme spans" only does
if you already knew.

Stopped `.claude/rules/knowledge-entries.md` restating the format that
`knowledge/index.md` specifies, since the two had begun to diverge on what is
required. The spec travels with the bundle; the rules file says how an entry gets
written here. Recorded the monthly log archive convention in both.

Repaired this file's own header, which had swallowed the heading of the entry
below it.

## [2026-09-21] Record

Made the dedup gate's Jev calls visible as `llm_call` rows under their own stage.
First live run: 47 of 188 candidates excluded, two requests at p50 512ms and p95
895ms against the dedup stage's p50 of 20.9s. Also recorded the rule the latency
read now follows, that a timeout counts as latency and a fast failure does not,
and why this endpoint's millisecond 529 is the case that forced the distinction.

## [2026-09-21] Lint

Full pass over all 23 entries. No contradictions, no orphans, no expired
`stale_after`, no em-dashes. Rewrote two entries that had started narrating
their own revisions rather than stating current knowledge
(`cliffhangers-must-defer`, `backchannels`). Pointed the one wikilink aimed at a
section index at the entry it meant, and linked the humour baseline to the rule
it names. Left open: `PromptVarietyPicker` is load-bearing in two entries with
no entry of its own, and the `ScriptJudge`/`AttentionScoring` split is explained
inline in three places instead of one.

## [2026-09-21] Record

Measured Jev as a script judge against the 19 archived episodes carrying a
stored score, prompted by LangChain's agent-eval comparison. Agreement with the
current judge follows what each question demands of a reader: teaser +0.62,
humor +0.29, cliffhangers +0.22, and a single whole-script rubric saturates with
12 of 19 at the top level. Recorded why the low-variance argument does not
transfer, which is that `ScriptJudge` returns positions and `AttentionScoring`
computes every number from them.

## [2026-09-21] Record

Attributed the Jev decisions endpoint's observed failures to one OpenRouter
incident on 2026-09-21, roughly 08:20-09:30 CEST, visible as a dip to about 75%
on OpenRouter's own availability graph for the model. A probe at 12:30 the same
day returned 200 in 686 ms with correct answers, so the endpoint's steady-state
failure rate is unmeasured and the earlier "weakest property by far" reading
described the incident. What the incident does establish is unchanged: a
single-provider model has no routing fallback, so callers need a defined
behaviour for having no answer.

## [YYYY-MM-DD] <operation>` so recent
activity can be read with `grep "^## \[" knowledge/log.md | head -10`.

## [2026-09-21] Record

Measured TypeSafe's Jev decision endpoint against the scoring stage: 40
articles through both the combined call and the split, and 80 articles above
and below the relevance threshold through Jev alone. The split costs 20% more
because both calls read the article, and a zero-loss pre-filter saves 5%. Jev
fits the closed-set classifications, not the scoring stage. Also recorded that
`score` takes an ordered rubric, not a checklist: getting that wrong inverts
the ranking.

Then measured the dedup decision the same way, with 20 provably already-covered
articles planted among 40 fresh ones. Batching all 60 questions against one
shared state costs a fifth of the current dedup call and returns 27 times
faster at the same detection; asking them one call per candidate costs twice
the current call. Recorded that the total money at stake across both stages is
cents per month, so the case rests on robustness and latency.

Built the gate (OpenSpec change `gate-dedup-with-jev`). On live traffic it
excludes 45 of 186 candidates in 1.6s and the whole dedup stage drops from
36.1s ungated to 11.8s, the clustering call speeding up too on the smaller
prompt. Recorded that chunks must be split evenly: a greedy fill sent a
second request carrying all 190 covered topics to ask about two articles.

The degradation path was then confirmed by an unforced outage rather than a
simulated one: a live run hit `529 system_overloaded` on both chunks and the
stage clustered all 186 candidates and carried on. That is the third
availability incident on this endpoint in one morning.

## [2026-09-17] Record

Measured whether the compose stage should return JSON turns instead of tagged
free text, after episode 222 shipped with its second half attributed to the wrong
speaker: [[evals/structured-output-for-scripts]]. Free text malformed its speaker
tags in three runs out of three; structured output did not, and the truncation
objection that had been argued against it did not reproduce. Structured output
loses control of script length instead, which is the axis that carries the TTS
cost, so it is not adopted yet.

## [2026-09-17] Record

A listener-reported repeat across two consecutive episodes turned out to be one
statistic carried by two unrelated articles, which article-level dedup cannot
see. Recorded as a new prompt-rules entry. The base rate is unmeasured: the scan
that reported none was reading a wrong endpoint and was discarded.

## [2026-09-17] Record

Extended the phoneme-span finding. The per-chunk STABLE guard has not closed the
literal-read failure in production, and the failure does not reproduce in an
isolated one-chunk request. The Inworld documentation's English-IPA-only
constraint is not the cause: a listening comparison judged the existing
Dutch-vowel dictionary value correct and the English-standard respelling wrong.

## [2026-09-15] Record

Ran the CURIOSITY HOOKS ablation, the first experiment the cache bypass makes
possible. Removing the bullet leaves real deferred hooks unchanged and cuts the
immediately-answered promises from 6 to 1, which the cliffhanger score cannot
see. The rule-rationale entry is no longer a hypothesis.

## [2026-09-15] Record

Recorded why the deep-dive research tool stays client-side. No JVM framework
covers the Responses API's server-side web search in a typed way, and the swap
would take back the call cap, the query cache, the per-user research key and a
cost we count ourselves.

## [2026-09-15] Record

Judged the whole archive and recorded the baseline. Humour is one speaker's job
in 91 of 162 episodes and 70 defer nothing, so the two rules written against
episode 208 now have numbers to move. Recorded alongside it why repeating a
prompt variant needs an explicit cache bypass.

## [2026-09-15] Record

Recorded what a Narro feed carries after reading one live: no conversation id
exists, the reply header names the account rather than the tweet, and 28 of 29
replies in a 50-item sample are self-threads. That distribution is what the
reply-target threading rule rests on.

## [2026-09-15] Record

Recorded the judge's first live calibration: its anchors for episode 209 match
the by-hand audit on every quantity, at 12 seconds and under a cent per script.

## [2026-09-15] Record

Recorded that turn length and episode length are judged by ear and will not
become gates, and that the length spread happens at a constant article count.

## [2026-09-15] Record

Implemented the deterministic metrics layer and ran it over the archive. Recorded
the baseline, which settles the episode length spread and shows that
backchannel-shaped turns predate the rule asking for them.

## [2026-09-15] Record

Split the scoring proposal into a free deterministic half and a judged half, so
the cheap layer does not wait on the expensive layer's migration and
calibration.

## [2026-09-15] Record

Recorded why a turn may not echo the previous speaker, from the episode 219
"Let's." turn. Noted that the backchannel device is what made a one-word turn
stop looking wrong, and that the structural metrics had already flagged it with
nothing reading them.

## [2026-09-15] Record

Added the backchannel device and the reference-show turn-length finding it rests
on, mined from the 2026-09-14 session where the reference analysis was done.

## [2026-09-15] Record

Corrected what the episode 209 defect actually is: the overlap between two
adjacent turns, not the adjacency itself. The structural merge built against the
earlier reading was removed again.

## [2026-09-15] Lint

First pass over the seeded bundle. All cross-references resolve and no entry is
unreachable from an index. Corrected em-dashes in the root index against the
project's writing conventions. Confirmed the log prefix is greppable and that
nothing under `src/` or `frontend/` reads `knowledge/`.

## [2026-09-15] Record

Seeded the bundle: skeleton and four sections, the TTS findings from the Inworld
probes, the rationale behind four composition rules, and the reference material
behind the humor and anchor-story work.
