## 1. Retry transient I/O errors

- [x] 1.1 Catch `ResourceAccessException` in `InworldTtsProvider.synthesizeWithRetry` and retry with the existing 1s/2s/4s backoff, rethrowing after the final attempt
- [x] 1.2 Log a warning with the I/O error message and attempt count on each retry

## 2. Evict stale pooled connections

- [x] 2.1 Add a class-level named `ConnectionProvider("inworld-tts")` with `maxIdleTime = 30s` in `InworldApiClient` and use it in `HttpClient.create(...)`

## 3. Verification

- [x] 3.1 Run `mvn test` and confirm all tests pass (874 passing)
- [x] 3.2 Restart the application with the new build
