## 1. Implementation

- [x] 1.1 Extend `buildModelNamesBlock()` in `ComposerUtils.kt` to cover package/repo/domain identifiers (label "MODEL, PRODUCT & PACKAGE NAMES")
- [x] 1.2 Add `buildHandlesBlock()` ("SOURCE NAMES, NOT HANDLES") to `ComposerUtils.kt`
- [x] 1.3 Add `buildResearchNamesBlock()` ("RESEARCH NAMES FOR THE EAR") to `ComposerUtils.kt`
- [x] 1.4 Wire all three blocks into `BriefingComposer`, `DialogueComposer`, and `InterviewComposer`

## 2. Tests

- [x] 2.1 Update model-names prompt assertions to the new label and add tests for the two new rules in all three composer test classes
- [x] 2.2 Run `mvn test` and confirm the full suite passes

## 3. Verify

- [x] 3.1 Restart the app (`./stop.sh` then `./start.sh`) and confirm it boots cleanly
