<!-- Implemented before this change was written; every task below is already done. -->

## 1. Post-processor

- [x] 1.1 Add `SOUND_TAG_ALIASES` mapping near-misses of the documented sound names onto their documented spelling, documenting on the map why an unfolded plural costs a turn-long mood
- [x] 1.2 Rewrite `normalizeSoundName` to fall back to that map after the exact-match check

## 2. Tests

- [x] 2.1 `folds a plural sound name onto the documented spelling` covering episode 207's `[laughs]` and `[chuckles]`
- [x] 2.2 `folds every known sound name variant` asserting each entry of the map
- [x] 2.3 `a folded sound name is a sound rather than a steering instruction`, including the contrast with a tag that stays an instruction
- [x] 2.4 `a genuine delivery direction is still an instruction`, pinning that `[warm and conversational]` and `[light]` are not folded
- [x] 2.5 Run `mvn test` and confirm the full suite passes
