## 1. Implementation

- [x] 1.1 Add `buildModelNamesBlock(): String` to `ComposerUtils.kt` next to `buildNumbersBlock()`, with the spoken-name rule and worked examples
- [x] 1.2 Append `${buildModelNamesBlock()}` after `${buildNumbersBlock()}` in `BriefingComposer.kt`
- [x] 1.3 Append `${buildModelNamesBlock()}` after `${buildNumbersBlock()}` in `DialogueComposer.kt`
- [x] 1.4 Append `${buildModelNamesBlock()}` after `${buildNumbersBlock()}` in `InterviewComposer.kt`

## 2. Tests

- [x] 2.1 Add/extend composer prompt tests asserting the spoken model names rule appears in each composer's built prompt
- [x] 2.2 Run `mvn test` and confirm the full suite passes

## 3. Verify

- [x] 3.1 Restart the app (`./stop.sh` then `./start.sh`) and confirm it boots cleanly
