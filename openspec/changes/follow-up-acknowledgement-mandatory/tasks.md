## 1. Compose rule

- [x] 1.1 Change the `WHAT COUNTS AS NEW` rule in `ComposerUtils.buildHistoryGuidanceBlock` to require a one-line acknowledgement of prior coverage for article groups with a `[FOLLOW-UP: ...]` header, grounded only in the header; verify with `mvn test`

## 2. Observation

- [ ] 2.1 On the next regular episodes with follow-up headers, check that the script acknowledges prior coverage and that the acknowledgement does not recur for the same story episode after episode
