# Tasks

## 1. Fix

- [x] 1.1 Treat a blank key as absent in `setConfig`. Reproduced first by `UserProviderConfigServiceTest` ("keeps the stored key when the key field is left blank", failed before the fix); `mvn test` passes (2026 tests).
