<!-- Implemented before this change was written; every task below is already done. -->

## 1. Find the JSON inside the response

- [x] 1.1 Add `private fun jsonStart(raw: String): Int?` returning the index of the first brace or bracket, or null when the response holds no JSON
- [x] 1.2 Read the strict parse from that index with a streaming parser, so a single value ends the read and a closing fence or trailing chatter never has to be located
- [x] 1.3 Document why the salvage reads to the end of the response rather than to its last closer (a truncated response's last closer sits inside the element it was cut off in)

## 2. Accept either response shape

- [x] 2.1 Add `private fun parseEitherShape(raw: String): DedupResult?`, reading a bare array as the cluster list and an object as `DedupResult`
- [x] 2.2 Route `parseOrSalvage` through `parseEitherShape`
- [x] 2.3 Seed `salvageClusters` with `inClusters = true` when the payload opens with an array, leaving the `clusters` property scan for the object shape
- [x] 2.4 Reword the failure message from "Truncated dedup response" to "Unparseable dedup response"

## 3. Distinct prompt per dedup attempt

- [x] 3.1 Add `internal fun promptForAttempt(prompt: String, attempt: Int)`, returning the prompt unchanged on attempt 1
- [x] 3.2 Add `jsonOnlyCorrection(attempt: Int)` naming the attempt and demanding the raw `{ "clusters": [ ... ] }` object with no prose or code fences
- [x] 3.3 Track the attempt number across the Resilience4j retry and route the `.user(...)` call through `promptForAttempt`
- [x] 3.4 Document the cache replay that makes an identical retry prompt a bug

## 4. Tests

- [x] 4.1 `TopicDedupFilterTest`: a response wrapped in prose and a json fence parses
- [x] 4.2 `TopicDedupFilterTest`: a bare cluster array parses
- [x] 4.3 `TopicDedupFilterTest`: the plain object the prompt asks for still parses
- [x] 4.4 `TopicDedupFilterTest`: a truncated bare array salvages its complete clusters
- [x] 4.5 `TopicDedupFilterTest`: a response holding no JSON fails the attempt, and one with chatter after the JSON still parses
- [x] 4.6 `TopicDedupFilterTest`: attempt 1 is unchanged, attempt 2 carries the correction, and attempt 3 differs from attempt 2
- [x] 4.7 Run `mvn test` and confirm the whole suite passes (1334 tests, 0 failures)

## 5. Verification against the running application

- [x] 5.1 Repoint the podcast's dedup override from the `~…-latest` alias to the pinned `deepseek/deepseek-v4-flash-0731` and delete the poisoned `llm_cache` row
- [x] 5.2 Restart the application and retry episode 202
- [x] 5.3 Confirm dedup completes on the first attempt with no parse warning: 93 candidates into 62 clusters selecting 84 articles in 16.6s
