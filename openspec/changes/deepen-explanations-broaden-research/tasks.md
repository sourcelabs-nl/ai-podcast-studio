## 1. Implementation

- [x] 1.1 Strengthen `buildAudienceBlock()` in `ComposerUtils.kt` into a directive with no fixed concept count
- [x] 1.2 Broaden `buildWebSearchBlock()` in `ComposerUtils.kt` (both variants) to target the 2-3 most newsworthy stories and 2-3 calls

## 2. Tests

- [x] 2.1 Update the deep-dive prompt assertion in `BriefingComposerTest` to the new "2-3 most newsworthy" wording
- [x] 2.2 Run the composer test classes and confirm they pass

## 3. Verify

- [x] 3.1 Restart the app (`./stop.sh` then `./start.sh`) and confirm it boots cleanly
