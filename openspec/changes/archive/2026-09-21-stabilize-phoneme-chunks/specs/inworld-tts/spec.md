## ADDED Requirements

### Requirement: Chunks carrying an IPA phoneme are synthesized in STABLE delivery
The `InworldTtsProvider` SHALL choose `deliveryMode` per chunk rather than once per script. A chunk whose text contains an IPA phoneme span SHALL be sent with `deliveryMode` `STABLE`. Every other chunk SHALL be sent with the podcast's configured delivery mode, unchanged.

A phoneme span is a literal instruction the engine is meant to follow exactly, and a wide delivery mode samples around it. Eight identical requests for a sentence ending in `/jɑrnoː/` on `CREATIVE` returned three manglings of the name, while the same eight on `STABLE` were all correct. Narrowing only the chunks that carry a phoneme keeps the rest of the episode at the expressiveness the podcast was configured for.

When the podcast configures no delivery mode, the provider SHALL NOT introduce one, because the request carries a temperature instead and a delivery mode replaces it.

Phoneme detection SHALL reuse the IPA span pattern that `TtsScriptSanitizer` already applies, via `TtsScriptSanitizer.containsPhoneme`, so the two agree on what counts as a phoneme.

#### Scenario: A phoneme chunk drops to STABLE
- **WHEN** a podcast configured with `deliveryMode: CREATIVE` has one turn reading `And I'm /jɑrnoː/.` and another reading `Welcome back.`
- **THEN** the phoneme turn is sent with `deliveryMode` `STABLE` and the other turn with `CREATIVE`

#### Scenario: A podcast without a delivery mode is untouched
- **WHEN** a podcast configures a temperature and no delivery mode, and a chunk contains a phoneme span
- **THEN** the request carries that temperature and no delivery mode
