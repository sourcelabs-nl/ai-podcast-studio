## Why

Inworld released TTS-2 on 2026-05-05 with new natural-language steering and a `deliveryMode` parameter (STABLE / BALANCED / EXPRESSIVE) replacing `temperature` for controlling expressiveness. To let podcasts opt into the new model and tune its delivery without falling back to free-text key-value entry, the system needs first-class config support and a dedicated UI control.

## What Changes

- Add `inworld-tts-2` model entry to `app.models.inworld` in `application.yaml` (cost-per-million-chars: $35) alongside the existing `inworld-tts-1.5-max` and `inworld-tts-1.5-mini` entries. The default model (`InworldTtsProvider.DEFAULT_MODEL`) stays at `inworld-tts-1.5-max`.
- Add optional `deliveryMode` field to Inworld TTS settings, accepted via `ttsSettings["deliveryMode"]`. When set, the provider forwards it as the `deliveryMode` field in the Inworld synthesize request and suppresses `temperature` (the Inworld API treats them as mutually exclusive on TTS-2). When unset, current behavior is preserved (default temperature 0.8).
- Refactor `InworldApiClient.synthesizeSpeech` to take optional knobs as a single `InworldSynthesisOptions(speed, temperature, deliveryMode)` data class instead of growing positional parameters.
- Add a "Delivery Mode" `Select` to the frontend podcast settings page (`/podcasts/[podcastId]/settings`), conditionally rendered when provider=`inworld` and the selected model is `inworld-tts-2`. Options: `—` (unset), `STABLE`, `BALANCED`, `EXPRESSIVE`. Wires to `form.ttsSettings.deliveryMode`; selecting `—` deletes the key.

## Capabilities

### New Capabilities
<!-- None — all changes extend existing capabilities. -->

### Modified Capabilities
- `inworld-tts`: provider now reads `ttsSettings["deliveryMode"]` and forwards it as `deliveryMode` in the synthesize request body; `temperature` is suppressed when `deliveryMode` is set.
- `model-registry`: `app.models.inworld` registry includes `inworld-tts-2` (type: tts, cost-per-million-chars: 35.00) in addition to the existing 1.5 max/mini entries.
- `frontend-podcast-settings`: TTS settings panel renders a dedicated Delivery Mode dropdown when the selected Inworld model is `inworld-tts-2`.

## Impact

- **Code**: `application.yaml`, `InworldApiClient.kt`, `InworldTtsProvider.kt`, `InworldTtsProviderTest.kt`, `frontend/src/app/podcasts/[podcastId]/settings/page.tsx`.
- **API**: No new endpoints. Existing `GET /config/defaults` already returns the registry, so the new model surfaces in `availableModels.inworld` without changes.
- **Database / migrations**: None. Existing podcasts continue using their configured model; `deliveryMode` is opt-in via the new UI control.
- **Cost tracking**: `inworld-tts-2` cost lookup works automatically via the existing `app.models.<provider>.<model>.cost-per-million-chars` mechanism.
- **Backwards compatibility**: Fully backwards compatible. Existing podcasts on 1.5-max/mini are unaffected. The `synthesizeSpeech` signature change is internal (single caller in `InworldTtsProvider`).
