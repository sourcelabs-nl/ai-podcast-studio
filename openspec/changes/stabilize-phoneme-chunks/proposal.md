## Why

Episode 210 read the host's name wrong. The script was correct: the pronunciation dictionary holds `Jarno → /jɑrnoː/`, the composer wrote the phoneme where the name belongs, and the sanitizer passed it through as a listed term. The engine simply did not honour it.

Nothing about that request was unusual. Replaying the shipped opening turn against the live API, byte-identical and with the same `deliveryMode: CREATIVE`, `enhanceGeneration: true`, `speakingRate: 1.0` and `language: en`, produced a correct reading. The difference is sampling, not input: eight identical requests for `It is Monday, the fourteenth of September, twenty twenty-six, and I'm /jɑrnoː/.` on `CREATIVE` came back with three manglings, and the same eight on `STABLE` were all correct. At roughly one failure in three for a name that appears two or three times an episode, most episodes mispronounce it at least once.

A phoneme span is not prose. It is a literal instruction the engine is meant to follow exactly, and `CREATIVE`, the widest emotional range Inworld offers, samples around it. Moving the whole podcast to `STABLE` would fix it by giving up the expressiveness the show is configured for across all 65 turns, to solve a problem that lives in one or two of them.

## What Changes

- `TtsScriptSanitizer` exposes `containsPhoneme(text)`, reusing the IPA span pattern it already owns.
- `InworldTtsProvider` chooses a delivery mode per chunk rather than once per script: a chunk containing an IPA phoneme span is sent as `STABLE`, and every other chunk keeps the podcast's configured mode.
- A podcast that configures no delivery mode is untouched. Its requests carry a temperature instead, and introducing a mode would silently discard it.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `inworld-tts`: gains a per-chunk delivery mode rule for chunks carrying an IPA phoneme.

## Impact

- Backend: `TtsScriptSanitizer.containsPhoneme` (new), `InworldTtsProvider.deliveryModeFor` (new, applied in `synthesizeAll`).
- Tests: `InworldTtsProviderTest` gains 2 cases.
- No schema, API payload shape, frontend, or configuration change. The podcast keeps `deliveryMode: CREATIVE`.
- Published episodes are unaffected; the fix applies to audio generated from now on.
