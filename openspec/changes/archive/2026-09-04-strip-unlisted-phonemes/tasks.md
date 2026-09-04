## 1. Strip unlisted phoneme spans

- [x] 1.1 Take the pronunciation dictionary in `TtsScriptSanitizer.sanitize` and drop any IPA span that is not one of its values
- [x] 1.2 Require a non-ASCII character and no internal whitespace in the span so ordinary prose is untouched
- [x] 1.3 Pass the dictionary from `TtsRequest.forPodcast` and from `PreviewAudioService`
- [x] 1.4 Record why this is enforced in code rather than by another prompt rule

## 2. Tests

- [x] 2.1 A listed span survives; an unlisted one is dropped
- [x] 2.2 Every span is dropped when no dictionary is configured
- [x] 2.3 `and/or`, `TCP/IP`, `input/output` and `24/7` are left alone
- [x] 2.4 A span does not reach across a line break
- [x] 2.5 Run `mvn test`

## 3. Housekeeping

- [x] 3.1 Replace the capability's placeholder Purpose

## 4. Verify against the real pipeline

- [x] 4.1 Regenerate episode 199's audio and confirm the invented span is gone while `/jɑrnoː/` survives
