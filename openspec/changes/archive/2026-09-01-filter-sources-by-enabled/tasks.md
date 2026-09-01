## 1. Filter in the query

- [x] 1.1 Add `findByPodcastIdAndEnabled(podcastId, enabled)` to `SourceRepository`
- [x] 1.2 Give `SourceService.findByPodcastId` an optional `enabled` and pick the query accordingly
- [x] 1.3 Add `@RequestParam(required = false) enabled: Boolean?` to `SourceController.list` and pass it through

## 2. Column-header filter in the Sources tab

- [x] 2.1 Add an Enabled-column dropdown with All / Enabled / Disabled, matching the episodes status filter
- [x] 2.2 Default it to Enabled and append `?enabled=` to the fetch
- [x] 2.3 Refetch when the selection changes

## 3. Tests

- [x] 3.1 Repository: `findByPodcastIdAndEnabled` returns only the matching sources
- [x] 3.2 Service: omitting `enabled` returns all, true/false narrow the set
- [x] 3.3 Controller: `?enabled=true` returns only enabled, omitted returns all
- [x] 3.4 Run `mvn test` and confirm the whole suite passes (1279 tests, 0 failures)
- [x] 3.5 Run `npx tsc --noEmit` in `frontend/`

## 4. Verify in the running app

- [x] 4.1 Confirm the tab opens with 24 rows, header reads "Enabled only"
- [x] 4.2 Confirm All shows 61 (header "Enabled") and Disabled shows 37 (header "Disabled only"); API returns 61/24/37
