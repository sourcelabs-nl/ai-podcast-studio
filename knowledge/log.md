# Log

Newest first. Each entry begins with `## [2026-09-21] Record

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
