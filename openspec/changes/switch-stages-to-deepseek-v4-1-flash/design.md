## Context

Stage defaults are the models every podcast without an override runs on, so a default that cannot route breaks generation outright, and one priced above what the stage needs is paid on every episode.

## Decisions

### One model for all three stages

Filter, dedup and compose have different shapes (per-article scoring, structured clustering, long-form prose), and until now ran on two models chosen separately. v4.1 Flash is the best available at each, so there is nothing to gain from keeping them apart, and a single default is one thing to verify against the routing floor rather than two.

The stages still differ where it matters: each states its own reasoning effort, `none` for the structured stages and the configured `app.compose.reasoning-effort` for composition.

### Price from a live probe, not the catalogue

The registry records the cheapest endpoint that routing can actually select, which is not the price a catalogue listing shows. The floor rejects any endpoint reporting an unrecognised quantization, and the account carries its own provider guardrails on top. Both were applied in a live probe, which routed to Novita.

## Risks

- Concentrating every stage on one model means one bad release affects the whole pipeline. Mitigated by the pinned slug: the default names a release, not a moving alias, so a new target cannot arrive unannounced with different reasoning defaults.
