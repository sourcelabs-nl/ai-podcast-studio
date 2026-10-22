# Tasks

## 1. Dropped-connection retry

- [x] 1.1 Add `app.source.connection-retry-delay` (default 3s) to `SourceProperties` and `application.yaml`.
- [x] 1.2 Wrap the fetch in `SourcePoller` so a `SocketException` anywhere in the cause chain is retried once after the delay. Verified by `a dropped connection is retried once and a successful retry counts as success` and `a dropped connection that persists on the retry counts as one transient failure`, both failing before the change.
- [x] 1.3 Verify an HTTP error status is not retried (`an HTTP error status is not retried`).

## 2. Deferral logging

- [x] 2.1 Log "is due for briefing generation" and "Deferring generation" at info the first time per podcast slot and at debug on re-checks. Verified by `a deferred slot is logged at info once and at debug on every re-check`.

## 3. Verification

- [x] 3.1 `mvn test` passes.
