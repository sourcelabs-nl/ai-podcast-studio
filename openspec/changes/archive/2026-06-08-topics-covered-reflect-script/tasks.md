## 1. Recap identifies discussed topics

- [x] 1.1 Extend `EpisodeRecapGenerator` to return `coveredTopics: List<String>` (add to `RecapResult`); update the prompt to append a delimited `|||COVERED_TOPICS|||[...]|||END_COVERED_TOPICS|||` block listing the exact candidate labels actually discussed, and parse it (reuse/mirror `TopicOrderExtractor`)
- [x] 1.2 Unit-test the parsing: covered subset extracted, recap text stripped of the block, empty/missing block tolerated

## 2. Prune non-discussed topics

- [x] 2.1 Add `EpisodeArticleRepository.clearTopicOrderForUncoveredTopics(episodeId, coveredTopics)` (`@Modifying` UPDATE setting `topic_order = NULL` where topic not in the covered set); no-op when `coveredTopics` is empty
- [x] 2.2 In `EpisodeService.generateAndStoreRecap`, after storing the recap, call the prune using `recapResult.coveredTopics`
- [x] 2.3 In `EpisodeService.regenerateRecap`, derive candidate labels from existing links (distinct topics with non-null `topic_order`, ordered) and pass them into recap generation

## 3. Verify

- [x] 3.1 `mvn test` green
- [x] 3.2 Restart, run `regenerate-recap` on episode 140, confirm feed + sources page list only the discussed topics
