> **Note:** This change documents work that was already implemented before the OpenSpec artifacts were created. All tasks below are complete.

## 1. Model registry

- [x] 1.1 Add `inworld-tts-2` entry under `app.models.inworld` in `src/main/resources/application.yaml` with `type: tts` and `cost-per-million-chars: 35.00`

## 2. Backend: parameter object refactor

- [x] 2.1 Add `InworldSynthesisOptions(speed, temperature, deliveryMode)` data class in `InworldApiClient.kt`
- [x] 2.2 Change `InworldApiClient.synthesizeSpeech` to take `options: InworldSynthesisOptions = InworldSynthesisOptions()` instead of separate `speed` and `temperature` positional args
- [x] 2.3 In the request body, send `deliveryMode` when set; otherwise send `temperature` if set (mutually exclusive)

## 3. Backend: provider integration

- [x] 3.1 In `InworldTtsProvider.generate`, read `ttsSettings["deliveryMode"]`, treat blank as unset, uppercase-normalize the value
- [x] 3.2 Build a single `InworldSynthesisOptions` from `ttsSettings` and propagate it through `generateMonologue`, `generateDialogue`, and `synthesizeWithRetry`
- [x] 3.3 When `deliveryMode` is set, suppress the `DEFAULT_TEMPERATURE = 0.8` fallback so temperature is not sent
- [x] 3.4 Update log statements to reflect the new options structure

## 4. Backend: tests

- [x] 4.1 Update existing call sites in `InworldTtsProviderTest` to use the new `InworldSynthesisOptions(...)` argument
- [x] 4.2 Add a test asserting that `deliveryMode` is forwarded and temperature is suppressed when set
- [x] 4.3 Add a test asserting that a blank `deliveryMode` is treated as unset (default temperature 0.8 applies)
- [x] 4.4 Verify all 725 backend tests pass via `mvn test`

## 5. Frontend: delivery mode UI

- [x] 5.1 In `frontend/src/app/podcasts/[podcastId]/settings/page.tsx`, add a conditional Delivery Mode `Select` rendered only when `ttsProvider === "inworld"` and `ttsSettings.model === "inworld-tts-2"`
- [x] 5.2 Wire the dropdown to `form.ttsSettings.deliveryMode` with options `— (provider default)`, `STABLE`, `BALANCED`, `EXPRESSIVE`
- [x] 5.3 When the user selects `— (provider default)`, delete the `deliveryMode` key from `ttsSettings` rather than persisting an empty string
- [x] 5.4 Verify the frontend type-checks via `npx tsc --noEmit`

## 6. Documentation

- [x] 6.1 Add a "Parameter objects" rule to project `CLAUDE.md` under "Architecture Guidelines"
- [x] 6.2 Add a generic version of the rule to global `~/.claude/CLAUDE.md` under "Software development"
- [x] 6.3 Save the parameter-objects feedback as an auto-memory and index it in `MEMORY.md`

## 7. Verification

- [x] 7.1 Restart the backend (`./stop.sh && ./start.sh`)
- [x] 7.2 Confirm `inworld-tts-2` appears in `GET /config/defaults` under `availableModels.inworld`
- [x] 7.3 Manually verify the Delivery Mode dropdown appears at `http://localhost:3005/podcasts/{id}/settings` after selecting `inworld-tts-2`
