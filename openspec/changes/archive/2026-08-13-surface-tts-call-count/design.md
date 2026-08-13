## Context

The per-episode cost breakdown (`CostsTab`) shows Calls/Input/Output/Cost per stage. The TTS row hard-codes `—` for Calls because the call count was never tracked. TTS providers already chunk the script and make one synthesis call per chunk (`TtsResult.audioChunks` holds one entry per call), so the count is available at generation time but discarded.

## Goals / Non-Goals

**Goals:**
- Record and display how many TTS synthesis calls an episode needed.
- Keep legacy episodes working (show `—`).

**Non-Goals:**
- Changing how TTS cost is computed (stays character-based).
- Counting retry attempts as separate calls (the count is chunks, the happy-path call count).

## Decisions

- **Derive the count from `ttsResult.audioChunks.size`** rather than adding a new field to `TtsResult`. Each chunk maps to exactly one synthesis call across all providers (Inworld, OpenAI, ElevenLabs), so the existing list size is the source of truth and needs no provider changes.
- **Nullable `tts_calls` column** (not `NOT NULL DEFAULT 0`) so legacy episodes are distinguishable as "unknown" and render `—`, consistent with how other legacy per-stage data behaves. The DTO collapses null → 0 for the frontend.
- **Persist in `TtsPipeline`** (both `generate` and `generateForExistingEpisode`) alongside the existing `ttsCharacters`/`ttsCostCents` writes, keeping the single write path.

## Risks / Trade-offs

- [Retries inflate real HTTP calls beyond chunk count] → Accepted: the count reflects synthesized chunks, which is the user-meaningful number; retries are transient and not billed differently.

## Migration Plan

- Flyway `V63` adds `tts_calls INTEGER` (nullable) to `episodes`. Additive, no backfill. Rollback is dropping the column (no code depends on it for legacy rows).

## Open Questions

- None.
