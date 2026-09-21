<!-- Implemented before this change was written; every task below is already done. -->

## 1. Cache write

- [x] 1.1 Add a private `store(entry: LlmCache)` to `CachingChatModel` that wraps `llmCacheRepository.save` and moves the existing debug log into it
- [x] 1.2 Catch a unique-constraint violation in `store`, log at debug that a concurrent call already wrote the entry, and rethrow anything else
- [x] 1.3 Add `isUniqueConstraintViolation`, unwrapping with `NestedExceptionUtils.getMostSpecificCause` and matching `SQLException.errorCode == 19`, with a comment on why the Spring exception type cannot be used
- [x] 1.4 Add the `SQLITE_CONSTRAINT_ERROR_CODE` constant and route the post-miss write through `store`

## 2. Tests

- [x] 2.1 `CachingChatModelTest`: a save throwing `UncategorizedSQLException` wrapping `SQLException(..., 19)` still returns the delegate response
- [x] 2.2 `CachingChatModelTest`: a save throwing an `UncategorizedSQLException` with an unrelated error code propagates
- [x] 2.3 Run the test class and confirm it passes (15 tests, 0 failures)
