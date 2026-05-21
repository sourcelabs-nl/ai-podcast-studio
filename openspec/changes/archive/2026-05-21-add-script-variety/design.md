## Context

`LlmPipeline.compose()` dispatches to `BriefingComposer`, `InterviewComposer`, or `DialogueComposer`, each of which builds a fully static prompt and calls `ChatClient.prompt().user(prompt).options(OpenAiChatOptions.builder().model(...).build()).call()`. No temperature is set. Prompts include literal example sentences (e.g. the "Coming up:..." sample in `InterviewComposer.kt`, the cliffhanger samples, the "Stephan, thanks as always" beat) which the LLM has been copying verbatim across daily episodes. The style-differentiating system message is one sentence per style; all engagement instructions (hook opening, front-loading, mid-roll callbacks, sign-off shape) are identical word-for-word across styles.

## Goals / Non-Goals

**Goals:**

- Break the structural sameness so consecutive episodes feel different at the seams.
- Keep regenerations of the *same* episode reproducible (same date + same articles → same scaffolding selection).
- Stay self-contained: no new dependencies, no new infra, no Spring AI tool-calling.

**Non-Goals:**

- Tool-calling, web search, history lookback. Those land in separate changes.
- Rewriting the engagement-technique guidance. Only the verbatim examples are removed; the abstract guidance stays.

## Decisions

### D1. Variety via `PromptVarietyPicker`, not pure temperature

Raising temperature alone keeps the structural beats (the prompt still mandates them). We deterministically rotate six discrete dimensions keyed on `hash(podcastId, episodeDate)`. Same episode regenerated → same selection (reproducibility); next day → different selection. Temperature (default 0.95) supplies micro-variation on top.

Alternatives considered:

- Pure temperature bump — keeps structural sameness; rejected.
- Random selection per call — breaks reproducibility for regenerations; rejected.
- LLM-side instruction to "vary the structure" — unreliable across runs; rejected.

### D2. Six rotation dimensions

The wording-overlap analysis of the last 10 episodes showed that the byte-identical repetition is not only at the formal bookends — much of it sits in interview-specific scaffolding (`"Coming up:" + 3 fragments`, `"Stephan, let's start with..."`, `"Stephan, thanks as always" / "Always a pleasure"`). Three axes catch the formal opener and close; we need three more to cover the middle.

Initial dimension sets (kept in code, not config):

- **Opening style:** cold-open question / shocking-stat / scene-set / contrarian take / first-person hook.
- **Transition vocabulary:** signposts vocabulary set A / set B / set C.
- **Sign-off shape:** recap-first / forward-look / call-to-action / quote-of-the-day.
- **Teaser shape** (interview-only, only kicks in with ≥5 articles): lead-with-question / curiosity-list / cold-tease / rhetorical-hook. Replaces the hardcoded `"Coming up: ..."` example today.
- **Topic-entry pattern** (interview/dialogue): straight-question / theme-bridge / contrast-pivot / micro-recap-then-pivot. Removes the literal `"Stephan, let's start with..."` lock-in.
- **Penultimate-exchange shape** (interview/dialogue): mutual-thanks / forward-look-handoff / single-sentence-callback / cold-handoff-to-sign-off. Removes the `"thanks as always" / "always a pleasure"` lock-in.

`hash(podcastId, episodeDate)` (SHA-256) is split across the six dimensions using different byte ranges so they rotate semi-independently. Briefing/casual/deep-dive/executive-summary styles only consume the first three axes; the interview/dialogue composers consume all six. The picker exposes typed enum values so composer code can `when`-match instead of stringly-comparing.

### D3. Default temperature 0.95, clamped `[0.0, 2.0]`

`0.95` matches Claude/GPT defaults for creative writing without being so loose it drifts off-topic. Per-podcast override lives inside a `composeSettings: Map<String, String>?` field on `Podcast`, mirroring the existing `ttsSettings` pattern. For v1 the only recognised key is `"temperature"`; the value is parsed as `Double` and clamped to `[0.0, 2.0]` server-side. Other keys persist but are ignored — this keeps the door open to `seed`, `topP`, etc. without future migrations. When the key is absent (or unparseable), the compose stage uses the system default from `app.briefing.default-temperature`.

### D4. Strip literal examples, keep abstract guidance

The interruption-style menu in `InterviewComposer` stays (excited, skeptical, confused, connecting dots, playful disagreement) — but the literal example sentences inside each bullet (e.g. `"Wait, wait — did you say 100x?!"`) are removed. Same treatment for the cliffhanger examples and the "Coming up:" example. The abstract instruction ("the interviewer should rattle off 2-3 short punchy fragments previewing the most interesting topics") remains.

### D5. Banned-phrase enforcement via blocklist + composer test

The "no verbatim example phrases" rule is enforced by a hardcoded `BannedPhrases` constant in the composer package plus a parameterized composer test that builds prompts for every `PodcastStyle` and asserts the resulting prompt string does not contain any banned phrase. Failing CI on regression is the goal — the LLM-judge approach was explicitly considered and deferred (would need a runtime, model-cost, and threshold conversation that's out of scope for this change).

The blocklist is the list of *prompt-side* example sentences we are removing. It is not a denylist of LLM *output* — the LLM may still produce similar sentences organically, and that is fine.

### D6. Migration is additive and nullable

`compose_settings TEXT` on `podcasts` is nullable JSON; null means "use the system defaults for every recognised key". No backfill needed. Storage uses the same `Map<String, String> ↔ JSON` converter already registered in `SqliteDialectConfig` for `ttsSettings`.

## Risks / Trade-offs

- **[Risk] Temperature 0.95 pushes some models off-topic.** → Mitigation: per-podcast override available, regression test on 5 fixture runs verifying no banned-phrase regressions and at least 3 distinct openings.
- **[Risk] Removing literal examples could leave the LLM under-instructed.** → Mitigation: keep the abstract guidance; verify with a manual run that the LLM still produces a "Coming up:" beat in the interview style.
- **[Trade-off] Variety picker rotates six axes, not more.** → Targets the actually-observed repetition; extend later if needed.
- **[Risk] Hash-based rotation lands on the same selection by collision on a particular date.** → Acceptable; consecutive-day distinctness is asserted in tests, not perfect uniqueness.
- **[Risk] No runtime check that the LLM actually varies the output.** → Acceptable for v1; the user will read episodes after a week of generation and we add an LLM judge in a follow-up change only if needed.

## Migration Plan

1. Land the new migration adding `compose_temperature` (additive, nullable, no backfill).
2. Deploy the new composers; existing podcasts continue to work because temperature falls back to the system default.
3. Frontend ships the new input; existing podcasts keep their (null) value until edited.

Rollback: revert code; the new column can stay (no other code depends on it) or be dropped in a follow-up.

## Resolved Questions

- **Dimension sets configurable per podcast?** No. Fixed sets in code for v1; revisit only if multiple users complain about the rotation.
- **`composeSettings["temperature"]` exposed in the upcoming-episode preview SSE?** No. Settings page is enough; the SSE preview shows progress, not config.
- **Default temperature?** `0.95`, clamped `[0.0, 2.0]`. Per-podcast override available.
- **Deterministic vs random+seeded picker?** Deterministic (SHA-256 of `podcastId + episodeDate`). Reproducibility for regenerations is required; random-with-seed would be functionally identical but expose a re-roll knob we don't need yet.
- **Banned-phrase enforcement: blocklist or LLM judge?** Hardcoded blocklist + composer test. The LLM-judge approach (runtime score on generated output) was considered and explicitly deferred — we'll only add it if a week of new episodes still feels same-y by ear.
- **More than six picker axes?** Not in v1. Six axes target the actually-observed repetition (formal opener, transitions, formal close, teaser, topic-entry, penultimate-exchange); add more only if the wording-overlap analysis re-runs and surfaces new gaps.
