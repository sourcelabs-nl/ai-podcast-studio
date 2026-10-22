# Proposal

## Why

On 2026-10-21 renaming subtopics in the podcast settings sent 14 of the 35 articles of episode 301 (including model releases and coding tools) into "Other". Scored articles keep the subtopic they were classified with, so a subtopic name that no longer exists counts as the synthetic "Other" bucket of weight 1. Those articles landed in the rapid-fire segment, and the script came out at 1320 words with one full segment. Nothing in the settings page said that a rename has this effect. Reclassifying the already-scored articles automatically was considered and rejected in favour of telling the user.

## What Changes

- The Compose tab of the podcast settings page shows an inline amber warning under the subtopics editor when a saved subtopic name is renamed or removed in the form. It names the affected old names and explains that already-scored articles keep their classified subtopic, count as "Other" (weight 1), and land in the rapid-fire segment or drop out of the episode until they are scored again.
- Changing a weight or adding a name does not trigger the warning.
- `findLostSubtopicNames(saved, edited)` in `frontend/src/lib/subtopic-names.ts` computes the names present in the saved subtopics and missing from the edited ones.

## Capabilities

### New Capabilities

### Modified Capabilities
- `podcast-customization`: adds "Settings page warns when subtopic names are lost".

## Impact

- `frontend/src/app/podcasts/[podcastId]/settings/page.tsx`, new `frontend/src/lib/subtopic-names.ts`. No backend, schema or API change.
