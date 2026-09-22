All tasks are complete: this change was written after the work, which is the
retrofit route CLAUDE.md allows for a localized change.

## 1. The shared frame

- [x] 1.1 Give `Panel` an optional `title` rendered inside its border, and delete `Section`
- [x] 1.2 Move the header tint into `TableHeader` and drop it from the four files that set it
- [x] 1.3 Wrap the episodes and sources tables in a `Panel`
- [x] 1.4 Convert every `Section` call site, dropping the title where the tab trigger already carries it (Script, Articles, Publications, Costs) and keeping it where a tab holds several cards (Latency, Evaluation) or names something the tab does not (Spent so far)

## 2. Tab actions on the tab row

- [x] 2.1 Give `SourcesTab` an `actionsSlot` and portal its download and add buttons into it
- [x] 2.2 Render the slot beside the `TabsList` on the podcast page, held in state so the portal runs once it mounts

## 3. Reading the evaluation, costs and latency tabs

- [x] 3.1 Add a score bar and a value-derived colour band to the overall score and each component
- [x] 3.2 Sort the costs table dearest first, rebuilding its body from a row list rather than seven hand-written rows
- [x] 3.3 Put the TTS character count in the Input column instead of spanning both token columns
- [x] 3.4 Sort the latency stage table by p99 and the request list by duration, slowest first, and correct the copy that said newest first

## 4. The Articles tab

- [x] 4.1 Group the articles by source into a row list carrying name, type, count and top relevance
- [x] 4.2 Render it as a table with an expandable row per source, reusing the episodes list's shape

## 5. The episode page

- [x] 5.1 Frame the show-notes summary like the upcoming-episode banner
- [x] 5.2 Derive the published sources page from the publication's audio URL and link it from the subtitle

## 6. The dedup gate's model

- [x] 6.1 Pass the configured gate model into the episode response mapper from `EpisodeController`
- [x] 6.2 Report it only for an episode whose gate issued requests
- [x] 6.3 Cover both cases in `EpisodeCostsMapperTest`

## 7. Writing it down

- [x] 7.1 Add `.claude/rules/frontend-layout.md` with the layout decisions, keyed to the frontend paths
- [x] 7.2 Name it in `CLAUDE.md`'s list of path-keyed rules
- [x] 7.3 Verify: `mvn test` and `npx tsc --noEmit`
