## 1. Bundle skeleton

- [ ] 1.1 `knowledge/index.md` with `okf_version: "0.2"`, the section list and the four conventional types with their expected frontmatter
- [ ] 1.2 Sections `knowledge/tts/`, `knowledge/prompt-rules/`, `knowledge/evals/`, `knowledge/references/`, each with its own `index.md`
- [ ] 1.3 `knowledge/log.md` with a fixed entry prefix carrying date and operation, newest first

## 2. Seed the TTS findings

- [ ] 2.1 Pace-reducing steering instructions: the 601-character turn, four samples per arm, 35.6-36.7s plain against 45.6-47.9s cued, no overlap, anchoring does not soften it
- [ ] 2.2 Unanchored opening cues: a steering instruction on a speaker's first turn over-commits because there is no `synthesisContext` to anchor it
- [ ] 2.3 Phoneme spans under `CREATIVE`: three of eight identical requests mangled the name, `STABLE` clean, the failing request byte-identical to a good probe
- [ ] 2.4 An undocumented sound tag becomes a steering instruction governing the rest of the turn
- [ ] 2.5 Each carries its method, `model_version: inworld-tts-2`, and `stale_after`

## 3. Seed the prompt-rule rationale

- [ ] 3.1 Why cliffhangers require deferral and an explicit park, naming the episode 208 failure shape, linked to the identifier in `InterviewComposer`
- [ ] 3.2 Why humor is not one speaker's job, from the reference-show analysis, linked to `ComposerUtils.buildHumorBlock`
- [ ] 3.3 Why the teaser names distinct topics from different parts of the episode
- [ ] 3.4 The suspected `CURIOSITY HOOKS` conflict, with no `verified` events and a body stating it is inferred from reading the prompt and one episode audit

## 4. Seed the references

- [ ] 4.1 The two Dutch two-host reference shows: what transfers (reactive humor between hosts, an anchor story reused as a throughline) and what does not (their conflict-free register)
- [ ] 4.2 The anchor-story device as a candidate not yet tried

## 5. The schema in CLAUDE.md

- [ ] 5.1 A section covering the three layers, the four types, and the provenance fields (`generated`, `verified`, `status`, `stale_after`)
- [ ] 5.2 The three operations: record within the task, file a worthwhile answer back, and lint
- [ ] 5.3 Point at `knowledge/index.md` rather than restating contents, keeping the per-session load bounded
- [ ] 5.4 One line on the boundary with the machine-local memory store: what belongs to the repo goes in `knowledge/`, what belongs to this machine and to how we work stays in memory

## 6. Exercise the loop once

- [ ] 6.1 Run a lint pass over the seeded bundle and act on what it reports
- [ ] 6.2 Confirm the log's prefix is greppable as intended
- [ ] 6.3 Confirm nothing in the application reads `knowledge/`

## 7. Backfill from older sessions (separate, do only if it still pays)

Independent of the seeding above, which draws on current knowledge and needs no mining. Four older sessions carry nearly all the historical signal (2026-08-21, 2026-08-25, 2026-09-07, 2026-09-10); the other transcripts are mostly tool output and dead ends.

- [ ] 7.1 Fix the questions first: which TTS measurements were made and with what outcome, and which prompt rules were tried and dropped. Search for answers to those, do not read the transcripts through
- [ ] 7.2 Delegate the search per session, requiring each hit to name its session and date, so a claim can be traced
- [ ] 7.3 Reconcile across sessions before writing: a conclusion reached early may have been overturned later
- [ ] 7.4 Write reformulated entries with `generated` naming the mining as its origin and no `verified` events until checked
- [ ] 7.5 Stop when the questions are answered rather than when the transcripts are exhausted
