# Design

## Where a candidate record lives

A candidate belongs in its own table, `episode_candidate_articles`, holding the
episode, the article and the outcome.

It cannot be a column on `episode_articles`. That table answers "which articles
does this episode cover", and seventeen call sites in main code read it on those
terms: the show notes, the sources file, the recap, the topic ordering, and
`markArticlesAsProcessed`. Adding dropped rows to it and expecting every reader
to filter them is the kind of change that works until one reader forgets, and
the reader that forgets marks a dropped article as processed or prints it in the
show notes.

The outcome is one of `USED`, `EXCLUDED_BY_GATE`, `DROPPED_AS_DUPLICATE`,
`CUT_BY_COMPOSE_CAP`. A unique constraint on `(episode_id, article_id)` holds
the one-episode rule that the windows already guarantee, so a bug that scored an
article twice fails loudly instead of doubling a cost.

## Carrying the outcome out of the dedup stage

The outcomes are known inside the dedup stage and thrown away there. The gate
returns what it excluded, the clustering call returns its clusters, and
`LlmPipeline` applies the compose cap. Each of those three already computes the
set it is removing in order to log how many it removed; the change is to return
it rather than only count it.

`DedupStageResult` therefore carries the dropped articles with their reason
alongside the surviving ones, and `EpisodeService` writes the candidate rows in
the same transaction that writes the article links.

## Costing the score stage over every candidate

`scoreStageCost` is already the shared operation; only its input set changes,
from `composeArticles.map { it.article }` to every candidate. Both token sums
move with it, so the persisted `score_input_tokens` and `score_output_tokens`
describe the same set as `score_cost_cents` rather than a subset of it.

The dropped part of that amount is not persisted. It is derived on the read path
from the candidate rows and the token counts on the articles they name, using
the same `CostEstimator.aggregateStageCost`. Persisting it would add a column
whose only job is to agree with a sum that is already stored, and two places
that must agree eventually do not.

The preview path (`LlmPipeline` line 532) calls `filter` with no episode,
because no episode exists yet. It records no candidates; the upcoming page
computes its figure from the articles standing rather than from candidate rows.

## The gate as a persisted stage

The gate gets the same column group as every other stage, plus a call count:
`dedup_gate_input_tokens`, `dedup_gate_output_tokens`, `dedup_gate_cost_cents`,
`dedup_gate_reported_cost_cents`, `dedup_gate_calls`.

Reading it from `llm_calls` instead was the obvious alternative and is wrong on
two counts. That log is deleted after 30 days (`app.llm.call-log.retention`)
while an episode keeps its costs for good, so the row would empty out a month
after every episode. And the episode's aggregate is defined as the sum of its
stage columns, written in one transaction; a stage sourced elsewhere cannot hold
that invariant.

The call count is persisted because, unlike the single-call stages, the gate's
request count is not derivable from its tokens: `JevClient` retries, and each
attempt is a real request. The other stages keep deriving `calls` as 1-if-
non-zero, which is what they have always done.

`LlmPipeline`'s `.plusReportedUsd(dedupResult.gateReportedCostUsd)` goes away.
The gate's resolved cost is carried out of the stage on its own and persisted on
its own. The episode's aggregate is unchanged by this: the same money is stored,
in two columns instead of one.

Existing episodes are not backfilled. Their `dedup_cost_cents` holds both
amounts with no record of the split, and the gate's own requests have aged out
of the log for all but the last month. A migration that apportioned them would
be guessing. Their gate row reads zero, which is what "not recorded" looks like
here, and the notice the Costs tab already shows for pre-breakdown episodes
covers the same ground.

## Attributing a request to an article

`llm_calls` gains `article_id`. This is what makes an episode's scoring latency
readable at all: a scoring request is issued when the article arrives, days
before the episode exists, so it carries no episode and an episode's request
list reports a scoring stage that issued nothing.

The episode's request list then gathers rows by episode or by an article among
that episode's candidates. The one-episode rule is what makes that safe: an
article is a candidate for exactly one episode, so a scoring request is claimed
by exactly one.

`ChatClientFactory` currently threads `episodeId` as a trailing nullable
parameter through `createForModel`, `buildCachingModel` and `CachingChatModel`.
Adding `articleId` beside it would make three signatures with two trailing
nullable `Long`s in a row, which is the shape that gets called with the
arguments the wrong way round. The two become one `LlmCallAttribution` value
instead, carrying the episode and the article, which is also what `JevCaller`
already is for the Jev side.

## Reporting the upcoming spend

The upcoming figure is computed on request from the articles the endpoint is
already returning, through the same `aggregateStageCost`. Nothing is stored and
no call is issued. That is what makes it comparable with the episode's number:
one operation, two sets of articles.

## Why the costs response stays a fixed shape

`EpisodeCostsResponse` keeps named fields rather than becoming a list of stages
keyed by name. The rows are not interchangeable: they are ordered deliberately
(the gate sits under the stage it relieves), they carry different columns (TTS
has characters, research has neither model nor tokens), and the scoring row
carries a breakdown none of the others have. A generic list would push all of
that into the frontend as a lookup table keyed by stage string, which is the
same coupling with an extra indirection.

The latency report is generic because its rows genuinely are interchangeable:
every stage has percentiles and a timeout and nothing else.

## Migrations

Three, none of them backfilling:

1. `episode_candidate_articles`, with its unique constraint and an index on
   `article_id` for the request-list lookup.
2. `article_id` on `llm_calls`, nullable, indexed.
3. The gate's five columns on `episodes`, `NOT NULL DEFAULT 0` except the
   reported cost, which is nullable like its siblings.

## What this change does not do

No new LLM call is issued anywhere. Every figure describes calls already made.
The gate's behaviour, the dedup clustering, the compose cap and the scoring
prompt are untouched; only what is recorded about them changes.
