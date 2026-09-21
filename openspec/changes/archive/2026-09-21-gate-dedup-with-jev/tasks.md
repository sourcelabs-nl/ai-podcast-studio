## 1. Jev client

- [x] 1.1 Add `JevProperties` under `app.llm.dedup` (enabled, decisions URL, model id, threshold, summary truncation, chunk character budget) with the measured defaults, and document each value's origin in `application.yaml` as the neighbouring entries do
- [x] 1.2 Add `JevClient` in `com.aisummarypodcast.llm` with its request and answer types, built on `RestClient.Builder` and resolving the OpenRouter key through `UserProviderConfigService`, following `TavilyClient`
- [x] 1.3 Make every failure path (missing key, non-success status, timeout, unparseable body) return an empty answer set with a logged warning, and verify no path throws
- [x] 1.4 Carry the response's input tokens and reported cost on the answer set, leaving the cost null when the call failed
- [x] 1.5 Unit-test the client against a mocked `RestClient`: a successful multi-question answer maps back by key, and each failure path returns empty without throwing

## 2. The gate in the dedup filter

- [x] 2.1 Add the gate to `TopicDedupFilter`, called before `buildPrompt`, building one state from the covered topics and the candidates with summaries truncated to the configured length
- [x] 2.2 Split the candidates into chunks that keep each serialised request under the character budget, repeating the covered topics per chunk, and merge the answers
- [x] 2.3 Exclude candidates whose answer is at or above the threshold; treat an unanswered candidate as not covered
- [x] 2.4 Log the candidate count before and after, and log a warning naming the reason when the gate returned nothing and the run went ungated
- [x] 2.5 Skip the gate entirely when the history carries no covered topics or the gate is disabled
- [x] 2.6 Unit-test the gate: a candidate above the threshold is absent from the prompt, one below it is present, one exactly at it is excluded, an empty answer set leaves every candidate, a partial answer set drops only what it answered for, and no covered topics means no request

## 3. Cost accounting

- [x] 3.1 Add the nullable gate cost in USD to `DedupFilterResult`, separate from the clustering call's `TokenUsage`
- [x] 3.2 Add it to `dedupReportedCostCents` in `LlmPipeline.dedup` after `CostEstimator.resolveLlmCost` has resolved the clustering call, keeping null as null
- [x] 3.3 Unit-test that a gated run's stage cost is the sum and an ungated run's is the clustering call's alone

## 4. Verification

- [x] 4.1 Run `mvn test` and fix every test the changed constructor signatures break
- [x] 4.2 Restart the app (`./stop.sh` then `./start.sh`) and run one real dedup through the preview path, confirming from the logs that the gate ran and how many candidates it excluded. Verified: 45 of 186 excluded in 1.6s, stage 11.8s against 36.1s with the gate disabled. The stage cost is covered by unit test only, because the preview path does no cost accounting; it is exercised for real on the next scheduled generation
- [x] 4.3 Confirm the degradation path against the live system by pointing the decisions URL at an unreachable host and checking the stage still completes with every candidate
- [x] 4.4 Run the code reviewer and repeat until clean (three passes: the chunking, the overrule, a misattached KDoc and a duplicated constant)

## 5. Found while verifying

- [x] 5.1 Balance the gate's chunks instead of filling greedily: a real run of 186 candidates against 190 covered topics split 184 and 2, and the second request carried the whole topic list to ask about two articles
- [x] 5.2 Report what the gate did on the summary line, so an overrule does not read as "0 gated out"
- [x] 5.3 Confirmed the degradation path against a real outage, not only the simulated one: a live run hit `529 system_overloaded` on both chunks and clustered all 186 candidates

## 6. Retries

- [x] 6.1 Add `JevTransientException` for the statuses worth another attempt (429, 5xx, 529), and classify the response status in `JevClient` so a rejected request is not retried
- [x] 6.2 Wrap the request in a `jev-decisions` Resilience4j retry inside the existing catch, so exhausting the attempts still returns no answers rather than raising
- [x] 6.3 Configure the instance in `application.yaml` with a 300ms base wait, short enough that the gate cannot hold up the stage it serves
- [x] 6.4 Test that a 529 and a 503 are retried and can succeed, that exhausted retries return no answers without raising, and that a 400 is attempted exactly once
