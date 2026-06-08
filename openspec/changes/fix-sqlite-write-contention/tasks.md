## 1. Tolerate overlapping SQLite writes (retrofit: already implemented)

- [x] 1.1 Raise `busy_timeout` to 30000ms in the SQLite JDBC URL in `application.yaml`
- [x] 1.2 Keep the default multi-connection Hikari pool (do not pin `maximum-pool-size: 1`)
- [x] 1.3 Remove `@Transactional` from `createEpisodeFromPipelineResult`, `finalizeEpisode`, `regenerateRecap`, and `regenerateAllShowNotes` so no transaction is held across TTS/LLM calls
- [x] 1.4 Run `mvn test` and verify the suite passes
- [x] 1.5 Restart the application so the new busy_timeout and code take effect
- [ ] 1.6 Verify a full episode generation (retry of the failed episode) completes without `SQLITE_BUSY` or connection-timeout errors
