## Context

See `proposal.md` for motivation and the measured benchmark and price data. The constraints that shaped the approach:

- `OpenRouterRouting`'s quantization floor admits only endpoints reporting fp8 or better. Vendor-native endpoints report `unknown`, so no OpenAI, Anthropic or Google model can route. Verified per model with `GET /api/v1/models/{id}/endpoints`.
- A registry entry serves two unrelated purposes: it is the price list for a *future* call, and the only way to resolve the cost of a *past* one. `ModelResolver` returns `cost = null` when a model is absent.
- The existing podcast overrides all three stages, so stage defaults affect only podcasts without overrides.
- The frontend groups `availableModels` by provider and picks an active provider from the keys.

## Goals / Non-Goals

**Goals:**

- Every stage default is a model that can actually route.
- The picker offers only models that can serve a request.
- Historical episode costs keep resolving.
- Prices reflect what routing can select.

**Non-Goals:**

- Relaxing the quantization floor (see `proposal.md`).
- Changing the existing podcast's models. Its overrides are its own choice, and it already runs on models that route.
- Deriving selectability at runtime by querying the provider (see Decisions).

## Decisions

**A curated `selectable` flag rather than a runtime capability probe.** Selectability could be computed by querying each model's endpoints at startup and testing them against the floor. Rejected: it makes startup depend on an external API, it would silently change the picker when a provider adds or drops an endpoint, and it conflates two things that should be edited together — the floor and the models chosen under it. A flag keeps the decision reviewable in the same file as the floor's rationale. The cost is that the flag must be revisited when the floor changes, which the KDoc says explicitly.

**Withhold rather than delete.** Deleting the six entries would remove them from the picker just as effectively, and is tempting since no current episode references them. Rejected because the failure it invites is silent: an episode generated on a deleted model reports a null cost rather than an error, and the `cost-tracking` capability's whole premise is that every stage's cost is attributable. Keeping the price and dropping the choice separates the two concerns the entry serves.

**Filter in a mapper file, not the controller.** The project's architecture rules keep business logic out of controllers, and its convention is a separate `*Mappers.kt` per package (`SourceMappers`, `PodcastMappers`, `PublishingMappers`). `toSelectableModels` follows that, so the controller keeps only its delegation and response assembly.

**Retain emptied provider keys.** `mapValues` preserves keys, so a provider whose every model is withheld yields an empty list rather than disappearing. This is deliberate and tested: the settings page derives its provider list and active provider from these keys, so a vanishing key would shift which provider it shows.

**Pin the July release rather than the floating alias for the defaults.** `~deepseek/deepseek-v4-flash-latest` prices identically to `deepseek-v4-flash-0731` at the eligible floor, which is evidence it currently resolves there. A pinned release was still chosen for the defaults, because an alias's target moves without notice and can bring different reasoning defaults with it — which is exactly how the dedup stage came to reason at high effort unnoticed.

## Risks / Trade-offs

- **The flag drifts out of date as providers add endpoints** → A newly fp8-served vendor model would stay hidden until someone flips the flag. Documented on `ModelCost.selectable`, and the failure mode is a model missing from a list rather than a broken generation.
- **A podcast already overriding a stage to a withheld model keeps failing** → Unchanged by this work either way; the flag governs only what is *offered*. No current podcast does this, and the resolver still prices it.
- **The refreshed prices are a snapshot** → They are a fallback only: OpenRouter reports the real cost per call and the pipeline prefers that wherever present, so drift affects pre-flight estimates and the cost gate, not billing accuracy.
- **`deepseek/deepseek-v4-flash-0731` is a pinned release that will itself age** → Accepted as the lesser risk against a floating alias, per the decision above.

## Migration Plan

Configuration and response-shaping only: no schema change, no data migration, no API contract removal. Deploy is `./stop.sh && ./start.sh`. Rollback is reverting the change.

Verified by restarting and reading `GET /config/defaults`: the three defaults report the new models, and `availableModels.openrouter` lists the six routable models with all six vendor-native entries withheld, while the TTS providers are untouched. Every model named as a default was separately probed against the live API with the production provider block and confirmed to route and to accept an explicit reasoning effort of `none`.
