---
okf_version: "0.2"
type: reference
title: Clef is a drop-in alternative to Jev on OpenRouter, and HostYourAI hosts both the scoring model and Jev-compatible decision models in the EU at about 2.5 times the cost per episode
answers: considering a different decision model than Jev, a fallback for a Jev outage, or EU-hosted inference for any pipeline stage
status: draft
method: >
  Desk research on 2026-10-20 over the Cloudflare launch post and Workers AI
  model docs for Clef, OpenRouter's model pages, the HostYourAI service, pricing,
  decision-model and catalogue pages, and OpenRouter's public /api/v1/models
  listing. The per-episode comparison reprices episode 297's usage as reported
  by the episode API (GET /users/{u}/podcasts/{p}/episodes/297, costs block). Nothing here
  was called from this project: no decisions request reached Clef and no request
  reached HostYourAI, so every quality and latency claim is the vendor's.
stale_after: 2026-12-20T00:00:00Z
generated:
  by: claude-opus-5-5
  at: 2026-10-20T00:00:00Z
---

# Clef and HostYourAI as alternatives

## Clef on OpenRouter

Cloudflare's Clef (27B, on Qwen3.8-27B) and Clef-flash (9B, on Qwen3.5-9B) are
open-weight decision models, Apache 2.0, on Hugging Face as `Cloudflare/clef` and
`Cloudflare/clef-flash`. They are served on OpenRouter as `cloudflare/clef` and
`cloudflare/clef-flash` on the same `POST /api/alpha/decisions` endpoint that
[[jev-decisions-endpoint]] describes, with the same `state` / `questions` request
and the same `noul`, `score` and `choice` answer shapes. Switching is a model id,
not a client.

Like Jev, they are absent from `GET /api/v1/models`. That listing only covers
chat models, so a model's absence from it says nothing about whether it can be
called on the decisions endpoint.

What transfers to this pipeline:

- **Availability.** OpenRouter's pages name two or three providers for Clef
  (Cloudflare, Prime Intellect, DeepInfra), against one for Jev. A provider
  outage of the kind seen on 2026-09-21 would have something to route to.
- **Question limit.** Workers AI documents at most 64 questions per request and
  a 65,536-token context. The dedup gate sends about 93 candidates per chunk,
  so it would need more chunks, and every chunk repeats the covered topics,
  which is the cost [[jev-decisions-endpoint]] warns about.
- **Price.** Workers AI lists $0.24 per 1M input tokens, output free. OpenRouter
  pages disagree between $0.24 and $0.042; the response's `usage.cost` settles it.

What does not transfer: the benchmark claims. Cloudflare reports Clef ahead of
Jev on closed-set classification (BANKING77 macro-F1 94.2 vs 79.7, CLINC150 97.4
vs 89.3) and behind on When2Call (72.4 vs 81.0) and BRIGHT retrieval (45.9 vs
47.5), with a median latency of 209 ms (Clef-flash 39 ms) against 524 ms for
Jev. None of it is independently reproduced. The closest public task to the
dedup gate's "already covered?" `noul` is retrieval relevance, where Clef is the
weaker of the two, and the gate's planted-case result for Jev is the bar any
replacement has to meet on this project's own candidates.

## HostYourAI for EU-hosted inference

HostYourAI serves open-weight models from EU data centres on an OpenAI- and
Anthropic-compatible API (`https://hostyourai.com/api/v1`, vLLM), prepaid, with
no minimum. Chat models run on a shared per-token Router that scales to zero or
on dedicated GPUs billed per minute. No SLA, latency target or cold-start time
is published.

### Chat models

The scoring model is on the Router as `deepseek-ai/DeepSeek-V4.1-Flash` at
€0.58 input, €0.15 cached and €1.73 output per 1M tokens, against OpenRouter's
list price from $0.035 input and $0.011 output. Quantization is not stated, so
whether it matches the fp8 floor in [[openrouter-routing]] is unknown. A
dedicated deployment needs 613 GB of VRAM, from €21.48 an hour.

### Decision models

A separate decision API, `POST /api/decision/v1/systemone`, is documented as
compatible with the Jev SDK for `choice`, `noul` and `score`, keeps requests on
an EU worker, and retains no input or output. Jev itself is not offered. Prices
are per 1M input tokens and appear only on the cards in the `/nl/models`
catalogue; the model detail pages return 404.

| model | size | context | price per 1M in | Dutch test (vendor, 120 q) | JevBench (EN) | availability |
|---|---|---|---|---|---|---|
| `hyai/loes-beslis` | 27B + LoRA | 16K | €0.15 | 98.3% | not published | warm |
| `hyai/nimble-9b` | 9B + LoRA | 2K | €0.05 | 95.8% | 61.8 | starts on use |
| `hyai/semif-qwen3.5-4b` | 4B | 4K | €0.03 | 90.8% | 74.7 | starts on use |
| `hyai/reflex-4b` | 4B | 4K | €0.03 | 89.2% | 71.7 | starts on use |
| `hyai/decider-2b` | 2B | 4K | €0.02 | 90.8% | 64.6 | starts on use |
| `hyai/laya` | 421M | 512 | €0.02 | 51.7% | 70.1 | starts on use |
| Jev 1.13, for comparison | | 32K | about $0.042 | | 75.4 | |

Context size, not price, decides what fits the dedup gate. One gate request
carries the covered topics as shared state plus about 93 candidates, around
32k tokens. Only Loes Beslis can hold the covered list at all, and at 16K it
needs smaller chunks, each repeating that list. The 4K and smaller models suit
per-article questions on an excerpt (news type, evergreen) and nothing that
carries shared context. SemIf additionally caps a request at 16 questions.

### What one episode would have cost

Episode 297's recorded LLM usage, priced at HostYourAI's Router rate for
DeepSeek V4.1 Flash and Loes Beslis in place of Jev, without cache discounts:

| stage | tokens in / out | OpenRouter, recorded | HostYourAI, computed |
|---|---|---|---|
| score (326 calls) | 1,355k / 118k | $0.217 | €0.99 |
| dedup | 53k / 4k | $0.006 | €0.04 |
| dedup gate | 64k / 0 | $0.003 | €0.01 |
| compose | 35k / 27k | $0.016 | €0.07 |
| recap | 3k / 0.5k | $0.001 | €0.003 |
| LLM total | 1,511k / 150k | $0.25 | about €1.11 |
| TTS and research | | $0.31 | $0.31 |
| episode | | $0.55 | about €1.42 |

Scoring is nearly all of the difference. At one episode a day that is about
$7.50 a month of LLM spend on OpenRouter against about €33 on HostYourAI, or
$16.50 against €43 with TTS and research. The gate row is a lower bound,
because the 16K context forces more chunks than Jev's two; cached input
(€0.15 per 1M) would lower the score row by an amount the recorded usage does
not show.
