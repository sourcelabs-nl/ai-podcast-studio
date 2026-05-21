## Context

Inworld's new TTS-2 model introduces a `deliveryMode` enum (STABLE, BALANCED, EXPRESSIVE) that controls expressiveness, replacing the `temperature` float used by TTS-1.x. The two parameters are mutually exclusive: per Inworld's API reference (`docs.inworld.ai/api-reference/ttsAPI/texttospeech/synthesize-speech`), `deliveryMode` is the TTS-2-specific control.

The current `InworldTtsProvider` reads `ttsSettings["model"]`, `ttsSettings["speed"]`, and `ttsSettings["temperature"]` and passes them as positional arguments through `InworldApiClient.synthesizeSpeech(userId, voiceId, text, modelId, speed, temperature)`. Adding `deliveryMode` as a 7th positional argument would worsen an already-long signature.

The frontend already has a generic `KeyValueEditor` for `ttsSettings`, so technically users can already enter `deliveryMode=EXPRESSIVE` manually. The user requested a first-class dropdown so the option is discoverable and constrained to valid enum values.

## Goals / Non-Goals

**Goals:**
- Allow podcasts to opt into `inworld-tts-2`.
- Provide a typed, discoverable UI control for `deliveryMode` (dropdown with the three valid enum values plus "unset").
- Forward `deliveryMode` to the Inworld API correctly — sent as `deliveryMode` in the request body, with `temperature` suppressed when set.
- Keep the change backwards compatible: existing podcasts and existing 1.5-max/mini behavior unchanged.

**Non-Goals:**
- Switching `InworldTtsProvider.DEFAULT_MODEL` to `inworld-tts-2` (kept on 1.5-max for cost reasons; users opt in per podcast).
- Migrating existing podcasts to TTS-2.
- Exposing other TTS-2-specific fields (e.g., natural-language voice steering, language hints) — out of scope for this change.
- Generic "settings schema per model" framework — only this one new field needs UI affordance right now; over-engineering avoided.

## Decisions

### D1. Wrap optional knobs in `InworldSynthesisOptions` data class
Instead of adding a 7th positional parameter to `synthesizeSpeech`, group `speed`, `temperature`, and `deliveryMode` into a single `InworldSynthesisOptions` data class with default-null fields.

**Rationale:** The signature was already at six parameters and growing. A parameter object keeps mandatory request identity (userId, voiceId, text, modelId) as positional args while making optional knobs extensible without ripple effects on call sites. This matches the project rule "wrap long parameter lists in a data class" (CLAUDE.md → Architecture Guidelines → Parameter objects).

**Alternatives considered:**
- *Add `deliveryMode: String?` as 7th positional arg.* Rejected — fragile under future additions, hurts call-site readability.
- *Builder pattern.* Rejected — Kotlin data classes with default args already give named-arg ergonomics; a builder is overkill.

### D2. `deliveryMode` and `temperature` are mutually exclusive in the request body
When `deliveryMode` is non-null, the API client omits `temperature` from the JSON body. When `deliveryMode` is null, behavior is unchanged (temperature included if set).

**Rationale:** Inworld's API reference documents `deliveryMode` as the TTS-2 replacement for `temperature`; sending both is undefined behavior. The provider also suppresses its `DEFAULT_TEMPERATURE = 0.8` injection when `deliveryMode` is set, so we don't accidentally always send both.

### D3. Uppercase-normalize `deliveryMode` in the provider
The provider applies `.uppercase()` to `ttsSettings["deliveryMode"]` and treats blank as unset.

**Rationale:** Inworld's enum values are uppercase (`STABLE` / `BALANCED` / `EXPRESSIVE`). Normalizing in the provider is forgiving for users who type values manually via the generic key-value editor. Blank treated as unset prevents stray empty strings from triggering the suppression of `temperature`.

### D4. Conditional UI: Delivery Mode dropdown shown only for `inworld-tts-2`
The dropdown is rendered only when `form.ttsProvider === "inworld" && form.ttsSettings.model === "inworld-tts-2"`. Selecting `—` deletes the `deliveryMode` key.

**Rationale:** The setting is meaningless for 1.5-max/mini and other providers (OpenAI, ElevenLabs). Showing it always would be misleading. Hiding it on non-tts-2 models avoids "ghost" settings that would silently get sent to APIs that don't understand them. Users can still set it manually via the existing generic `KeyValueEditor` if they want to override on a future model.

### D5. Pricing: $35 per million characters
Set `cost-per-million-chars: 35.00` for `inworld-tts-2`, matching Inworld's public on-demand price.

**Rationale:** Existing 1.5-max ($10) and 1.5-mini ($5) entries reflect enterprise/discount tier. The user is on whichever tier their Inworld account uses, but the public on-demand list price is the conservative default — actual cost tracking will be at most overestimated. If the user's account is on a different tier, they can adjust later.

## Risks / Trade-offs

- **Risk:** Existing 1.5-max/mini cost entries ($10/$5) are stale relative to Inworld's current public pricing ($35/$25 on-demand). → Mitigation: out of scope for this change; flagged to user, they can update separately if their plan tier changes.
- **Risk:** Users on 1.5-max who manually set `deliveryMode` in the generic key-value editor will have their value sent to an API that doesn't understand it. → Mitigation: low risk — Inworld API silently ignores unknown fields per OpenAPI spec; the dedicated dropdown is hidden for 1.5-max so this only happens if a user deliberately uses the advanced editor.
- **Trade-off:** No model-aware settings schema framework. The conditional rendering is hardcoded to one model. → Acceptable — premature abstraction would cost more than it saves; revisit if a 3rd model-specific setting appears.

## Migration Plan

- **Deploy:** Backend restart picks up the new yaml entry (registry re-binds on startup). Frontend rebuild required. No DB migration.
- **Rollback:** Revert the three source files. Existing podcasts unaffected since `deliveryMode` is opt-in.
- **Existing podcasts:** None affected. If a podcast is later switched to `inworld-tts-2` without setting `deliveryMode`, Inworld applies its own default (BALANCED per docs).
