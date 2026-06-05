## 1. Compose-time script cleanup (retrofit: already implemented)

- [x] 1.1 Add `stripOutsideSpeakerTags()` to `ComposerUtils.kt` (per-turn regex, span trim, untagged pass-through)
- [x] 1.2 Add `stripLeadingMetaCommentary()` to `ComposerUtils.kt` (conservative first-paragraph heuristic with length cap)
- [x] 1.3 Apply `stripOutsideSpeakerTags` in `InterviewComposer` and `DialogueComposer` composition results
- [x] 1.4 Apply `stripLeadingMetaCommentary` in `BriefingComposer` composition result

## 2. Validation

- [x] 2.1 Add `ScriptCleanupTest` (10 unit tests: preamble/trailing removal, mixed-tag endings, between-turn preservation, false-positive guards)
- [x] 2.2 Run `mvn test` and confirm the full suite passes (874 tests green)
- [x] 2.3 Restart the application and regenerate an episode live (episode 138) to confirm the stored script starts at the first speaker tag
