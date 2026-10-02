# Tasks

## 1. Pipeline completion

- [x] 1.1 Guard the feed export in `TtsPipeline` and the generated and created events in `AudioGenerationService` and `EpisodeService`, and verify with tests that each failure leaves the episode GENERATED
- [x] 1.2 Skip synthesis in `finalizeEpisode` when the audio file exists, and verify with a test that no text-to-speech call is made and the audio path is kept

## 2. Status and aggregation

- [x] 2.1 Read the pipeline status from the persisted stage, remove `PipelineStateTracker`, and verify with a repository test and two controller tests
- [x] 2.2 Store aggregated articles with `INSERT OR IGNORE` and re-read, and verify with a real-SQLite test that a duplicate inside a transaction commits and with aggregator tests for the race and the refused row

## 3. Completion

- [x] 3.1 Run `mvn test` and restart the app
