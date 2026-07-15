## 1. Compose input cap and timeout

- [x] 1.1 Add `maxArticles` (default 40) to `ComposeProperties` and `compose.max-articles` to `application.yaml`
- [x] 1.2 Add `capForCompose` helper in `LlmPipeline` (sort by relevance desc, take `maxArticles`)
- [x] 1.3 Apply the cap in `dedup()` before deriving follow-up annotations, topic labels, token totals, and links
- [x] 1.4 Apply the cap at the top of `compose()` so retry-from-compose is also bounded
- [x] 1.5 Raise the compose `ChatClient` request timeout from 10 to 20 minutes in `ChatClientFactory`
- [x] 1.6 Add tests: dedup caps to top-N, compose caps to top-N

## 2. Inworld TTS transient 5xx retry

- [x] 2.1 Add `InworldTransientException`
- [x] 2.2 Map HTTP 5xx (500–599) to `InworldTransientException` in `InworldApiClient.handleError`
- [x] 2.3 Retry `InworldTransientException` in `InworldTtsProvider.synthesizeWithRetry` with the existing backoff
- [x] 2.4 Add tests: 5xx retry-then-succeed, and throw after exhausting retries

## 3. Verification

- [x] 3.1 `mvn test` passes (941 tests)
- [x] 3.2 Verified end-to-end: episode 170 composed within the timeout after the 62→40 cap and generated successfully
