## 1. Humor block rework (retrofit: already implemented)

- [x] 1.1 Rename `buildToneBlock()` to `buildHumorBlock()` in `ComposerUtils.kt` and rewrite it as a concrete, countable `HUMOR & TONE` rule (2-3 story-tied humor moments, flavour menu, joke hygiene, Friday extra beat) with KDoc explaining the placement rationale
- [x] 1.2 Move the humor block to the first engagement bullet in `InterviewComposer.kt` and remove it from the SIGN-OFF bullet
- [x] 1.3 Move the humor block to the first engagement bullet in `DialogueComposer.kt` and remove it from the SIGN-OFF bullet
- [x] 1.4 Move the humor block to the first engagement bullet in `BriefingComposer.kt` and remove it from the SIGN-OFF bullet
- [x] 1.5 Reword the "Emphasis on important news" instruction in all three composers so it tempers emphasis only and explicitly does not suppress the playful tone

## 2. Validation

- [x] 2.1 Run `mvn test` and confirm the full suite passes (864 tests green)
- [x] 2.2 Restart the application and regenerate a previously generated episode (episode 132, Friday) via `POST .../episodes/132/regenerate` to compare old vs new script
- [x] 2.3 Verify the regenerated script (episode 137) contains 2-3 story-tied humor moments and the Friday end-of-week acknowledgement
