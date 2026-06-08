## Design

Single-string prompt tweak in `buildHumorBlock()` in `ComposerUtils.kt`; no structural changes.

- The `fridayExtra` string keeps the "one extra humorous beat, energy a notch higher" instruction.
- The "acknowledge the end of the work week (weekend plans, winding down, drinks with friends) in the opening or sign-off" clause is replaced by: the end of the week may be acknowledged only conversationally and in passing ("It's the end of the week...", "What a week..."), never as a direct greeting or shout-out like "Happy Friday".
- The function KDoc is updated to record the listener feedback driving the rule, so the constraint is not "simplified away" later.
- No code outside the string and KDoc changes; the Friday/non-Friday gating via `LocalDate.now().dayOfWeek` is unchanged.
