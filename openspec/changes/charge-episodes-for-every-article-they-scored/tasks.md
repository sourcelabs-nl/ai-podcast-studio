# Tasks

## 1. Record what became of every candidate

- [x] 1.1 Add the `episode_candidate_articles` migration: episode, article,
  outcome, unique on `(episode_id, article_id)`, indexed on `article_id`; verify
  with a migration test that the table exists and the unique constraint rejects a
  duplicate pair
- [x] 1.2 Add the entity and repository, with a read of one episode's candidates
  and a read grouped by outcome; verify with a repository test that both return
  what was written
- [x] 1.3 Carry the dropped articles and their reason out of the dedup stage on
  `DedupStageResult`: the gate's exclusions, the clustering call's duplicates and
  the compose cap's cut; verify with a unit test that each of the three reasons
  arrives with the articles it removed
- [x] 1.4 Write the candidate rows in the same `EpisodeService` transaction that
  writes the article links; verify with a test that an episode generated from 10
  candidates of which 6 compose records 10 candidates and 6 links, and that a
  failure writing the links leaves no candidate rows
- [x] 1.5 Verify a dropped candidate reaches no reader of the article links:
  assert the show notes, the sources file and `markArticlesAsProcessed` are
  unchanged for an episode with dropped candidates

## 2. Charge the episode for every candidate it scored

- [x] 2.1 Cost the score stage over every candidate rather than the survivors,
  moving the token sums with it; verify with a test that an episode scoring 188
  candidates and composing 121 persists the tokens and cost of all 188
- [x] 2.2 Report the dropped part as a breakdown of the score row, derived on the
  read path from the candidate rows and the articles they name; verify with a
  test that `calls`, `droppedCalls` and `droppedCostCents` describe the same
  episode and that the dropped cost is not added to `totalCostCents`
- [x] 2.3 Verify an episode with no candidate rows still reports: assert an
  episode generated before this change reports its linked article count as
  `calls`, zero `droppedCalls`, and the figures it was stored with

## 3. Cost the dedup gate as its own stage

- [x] 3.1 Add the gate's columns to `episodes` by migration:
  `dedup_gate_input_tokens`, `dedup_gate_output_tokens`,
  `dedup_gate_cost_cents`, `dedup_gate_calls` (NOT NULL DEFAULT 0) and
  `dedup_gate_reported_cost_cents` (nullable); verify with a migration test that
  an existing episode reads zero and null
- [x] 3.2 Carry the gate's usage, cost and request count out of the dedup stage
  separately and persist them through the existing `EpisodeService` path, and
  remove `plusReportedUsd(gateReportedCostUsd)` from the dedup cost; verify with
  a test that the dedup row holds the clustering call alone, the gate row holds
  the gate, and the episode aggregate equals what it was when the two were added
  together
- [x] 3.3 Count each gate attempt as a request, so a retried call reports two;
  verify with a test that a 529 followed by a success persists `dedup_gate_calls`
  of 2
- [x] 3.4 Verify a disabled gate costs nothing: assert an episode generated with
  no gate configured persists zeros and a null reported cost

## 4. Attribute a request to its article

- [x] 4.1 Add `article_id` to `llm_calls` by migration, nullable and indexed;
  verify with a migration test that existing rows read null
- [x] 4.2 Replace the trailing `episodeId` threaded through `ChatClientFactory`,
  `buildCachingModel` and `CachingChatModel` with an `LlmCallAttribution` value
  carrying the episode and the article; verify `mvn test` passes and no call site
  passes two bare nullable ids
- [x] 4.3 Name the article in `ArticleScoreSummarizer`'s requests; verify with a
  test that scoring one article records a row naming it and no episode
- [x] 4.4 Gather an episode's requests by episode or by an article among its
  candidates; verify with a test that the scoring requests of both used and
  dropped candidates appear in that episode's request list, and that a request
  appears in one episode's list only

## 5. Report the spend before the episode exists

- [x] 5.1 Report the scoring requests, tokens and cost of the standing candidates
  on the upcoming articles response, computed through
  `CostEstimator.aggregateStageCost`; verify with a test that the figure matches
  what the same articles would cost as an episode's score stage
- [x] 5.2 Verify nothing standing reports zero rather than failing, and that no
  LLM call is issued to answer the request

## 6. Show it

- [x] 6.1 Add the `dedupGate` row to `EpisodeCostsResponse` and the score row's
  `droppedCalls` and `droppedCostCents`; verify with an API test that the
  response carries them and that `totalCostCents` counts the score row once
- [x] 6.2 Render the Dedup gate row directly beneath Dedup in the episode costs
  tab, and the dropped breakdown inside the Scoring row, hidden when zero
- [x] 6.3 Add the Costs tab to the upcoming content page, showing the Scoring row
  alone and stating when nothing has been scored yet
- [x] 6.4 Verify the frontend types and the build: `npx tsc --noEmit` passes

## 7. Verify end to end

- [x] 7.1 Run `mvn test` and confirm it is green
- [ ] 7.2 Restart the application, generate an episode, and confirm its Costs tab
  shows the dedup gate beside dedup, a scoring row covering every candidate with
  its dropped breakdown, and a total unchanged in kind from before
- [ ] 7.3 Confirm the upcoming page's Costs tab reports the standing candidates
- [ ] 7.4 Record in `knowledge/` what the first run measured: what the gate cost
  against the clustering call it relieves, and what share of an episode's scoring
  spend went to candidates that never reached the script
