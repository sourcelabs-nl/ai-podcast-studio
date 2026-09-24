## Context

`buildHistoryGuidanceBlock` in `ComposerUtils.kt` emits the `WHAT COUNTS AS NEW` rule into every compose prompt. The `[FOLLOW-UP: ...]` headers it refers to come from `previousContext` in the dedup clustering response, stored in `episode_articles.follow_up_context`.

## Decisions

- **Mandatory, but short.** The rule asks for one line acknowledging prior coverage, then moves on to what is new. A longer recap would spend airtime on material the listener already heard.
- **Grounded only in the header.** The rule forbids stating when a story aired or what was said beyond the header. An earlier permissive version that trusted keyword hits produced a false "covered yesterday" claim in episode 197.
- **Wording change only.** No test asserts the rule text beyond its `WHAT COUNTS AS NEW` label, so no test changes.

## Risks / Trade-offs

- Identical follow-up texts recur across consecutive episodes from 208 onward. If the same continuation is matched repeatedly, a mandatory rule could make "we covered this before" appear in episode after episode. This is watched on the upcoming episodes rather than prevented up front.
- Effect on scripts is not measured before release; existing episodes are deliberately left as they are.
