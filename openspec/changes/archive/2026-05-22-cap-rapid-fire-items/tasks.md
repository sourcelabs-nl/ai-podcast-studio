## 1. Schema and configuration

- [x] 1.1 Add Flyway migration `V58__add_podcast_rapid_fire_max_items.sql` adding nullable `rapid_fire_max_items INTEGER` column to `podcasts`
- [x] 1.2 Add `rapidFireMaxItems: Int? = null` to `Podcast` entity
- [x] 1.3 Add `rapidFireMaxItems: Int = 6` to `ComposeProperties` and `compose.rapid-fire-max-items: 6` to `application.yaml`

## 2. Planner

- [x] 2.1 Add `RapidFireItem(article, bucketName, bucketWeight)` data class and `rapidFireOrder: List<RapidFireItem>` to `SubtopicPlan`
- [x] 2.2 Extend `SubtopicPlan.from` with `rapidFireMaxItems: Int` parameter; flatten rapid-fire articles, rank by `(bucket.weight DESC, article.relevanceScore DESC nulls last, article.id ASC)`, keep top N
- [x] 2.3 Rebuild rapid-fire buckets from kept items so `articleSubtopics` excludes dropped articles
- [x] 2.4 Update `RapidFireStyle` to a single `intro` string and rewrite `buildSubtopicPlanBlock` to enumerate kept articles as a numbered list with `~{wordsPerItem} words per item`

## 3. Composer wiring

- [x] 3.1 Update `BriefingComposer`, `DialogueComposer`, `InterviewComposer` to pass `podcast.rapidFireMaxItems ?: appProperties.compose.rapidFireMaxItems` to `SubtopicPlan.from`

## 4. API and persistence

- [x] 4.1 Add `rapidFireMaxItems: Int?` to `CreatePodcastRequest`, `UpdatePodcastRequest`, and `PodcastResponse`
- [x] 4.2 Map field in `Podcast.toResponse()`
- [x] 4.3 Wire field in `PodcastController.create` and `PodcastController.update`
- [x] 4.4 Add `validateRapidFireMaxItems` (range 0-50) and call it from create/update

## 5. Frontend

- [x] 5.1 Add `rapidFireMaxItems?: number` to `Podcast` TypeScript type
- [x] 5.2 Send `rapidFireMaxItems` in the settings page PUT payload
- [x] 5.3 Render a "Rapid-fire Max Items" `FieldGroup` in the Compose Settings tab with placeholder "6 (system default)"

## 6. Tests

- [x] 6.1 Update existing `SubtopicPlanTest` cases to pass the new `rapidFireMaxItems` argument
- [x] 6.2 Add tests: cap drops lowest-priority articles, ranking prefers higher relevance score within same bucket weight, cap of zero disables the tier
- [x] 6.3 Add test asserting the rapid-fire prompt block lists kept articles in priority order with `exactly N items` and `~X words per item`
- [x] 6.4 Run `mvn test` — all 835 tests pass

## 7. Smoke test

- [x] 7.1 Restart app via `./stop.sh && ./start.sh`
- [x] 7.2 Verify GET podcast response exposes new `rapidFireMaxItems` field
- [x] 7.3 Run SSE preview against The Daily Agentic AI Podcast; confirm closing segment has ≤ 6 items, each in its own expert turn, ~40-55 words per item, priority-ordered
