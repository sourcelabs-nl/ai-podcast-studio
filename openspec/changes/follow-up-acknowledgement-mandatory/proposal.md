## Why

The dedup stage marks a story that continues an earlier episode with a `[FOLLOW-UP: ...]` header, and the compose prompt only *permits* the script to mention that prior coverage. An audit of episodes 195 to 230 found the header acknowledged in 1 of 3 checked scripts: continuation stories (Google's zero-trust agent guide in episode 220, Meta Muse in episode 229) aired as fresh news, and none of the 8 most recent scripts contained any reference to earlier coverage. See `knowledge/evals/history-accuracy-audit-2026-09.md`. The history itself was accurate; the composer simply did not use it.

## What Changes

- The `WHAT COUNTS AS NEW` compose rule requires the script to acknowledge prior coverage when introducing an article group that carries a `[FOLLOW-UP: ...]` header: one short line in the host's own words, then the time goes to what is new.
- The acknowledgement is grounded only in the header. The script never invents when a story aired or what was said, preserving the guard added after the episode 197 "covered yesterday" error.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `article-dedup-filter`: the annotation-primacy requirement changes from allowing to requiring an acknowledgement of prior coverage for annotated article groups.

## Impact

- `ComposerUtils.buildHistoryGuidanceBlock`, reaching the briefing, dialogue and interview composers.
- No schema, API or configuration change. Existing episodes are not regenerated; the effect is observed on upcoming regular episodes.
