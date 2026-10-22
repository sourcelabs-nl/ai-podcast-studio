# Tasks

## 1. Helper

- [x] 1.1 Add `findLostSubtopicNames(saved, edited)` in `frontend/src/lib/subtopic-names.ts`, returning saved names missing from the edited map and tolerating null or undefined maps.

## 2. Settings page

- [x] 2.1 Show the amber warning under the subtopics editor on the Compose tab when `findLostSubtopicNames` is non-empty, with the explanation and an "Affected:" line listing the old names.

## 3. Verification

- [x] 3.1 `npx tsc --noEmit` passes. No frontend test runner exists, so the behaviour was verified in the browser on 2026-10-21: on load no warning, a weight-only change shows no warning, renaming Harness to HarnessX shows the warning with "Affected: Harness", and after a reload the warning is gone and nothing was saved.
