<!-- Implemented before this change was written; every task below is already done. -->

## 1. Phoneme detection

- [x] 1.1 Expose `TtsScriptSanitizer.containsPhoneme(text: String): Boolean` over the existing `PHONEME_SPAN` pattern, documenting why the provider needs it

## 2. Per-chunk delivery mode

- [x] 2.1 Add `InworldTtsProvider.deliveryModeFor(text, configured)`, returning `STABLE` for a chunk with a phoneme span and the configured mode otherwise
- [x] 2.2 Leave a podcast with no configured delivery mode alone, so its temperature is not discarded
- [x] 2.3 Apply it per chunk in `synthesizeAll`, alongside the existing per-chunk `previousRequests`
- [x] 2.4 Record the measured failure rates (3/8 on `CREATIVE`, 0/8 on `STABLE`) in the KDoc

## 3. Tests

- [x] 3.1 `InworldTtsProviderTest`: a chunk with a phoneme is sent `STABLE` while its neighbour keeps `CREATIVE`
- [x] 3.2 `InworldTtsProviderTest`: a podcast without a delivery mode keeps its temperature on a phoneme chunk
- [x] 3.3 Run `mvn test` and confirm the full suite passes (1415 tests, 0 failures)

## 4. Deploy

- [x] 4.1 Restart the application so subsequent generations use the new pipeline
