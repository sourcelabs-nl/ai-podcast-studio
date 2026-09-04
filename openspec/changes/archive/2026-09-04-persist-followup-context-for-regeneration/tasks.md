## 1. Persist the annotation

- [x] 1.1 Migration `V66` adding `follow_up_context TEXT` to `episode_articles`
- [x] 1.2 Add `followUpContext` to `EpisodeArticle` and to `insertIgnore`
- [x] 1.3 Persist `FilteredArticle.followUpContext` in `saveDedupResults`
- [x] 1.4 Carry the annotations on `PipelineResult` and persist them in `saveEpisodeArticleLinks`, so a regenerated episode stores them too

## 2. Feed them back into a regeneration

- [x] 2.1 Read the column in `findLinkedArticlesAndTopics` and add the map to `LinkedArticlesResult`
- [x] 2.2 Accept `followUpAnnotations` in `LlmPipeline.recompose` and pass it to all three composers
- [x] 2.3 Pass `linked.followUpAnnotations` from `runRegeneration`
- [x] 2.4 Wrap the recompose dispatch in the `compose` retry too — it is a composition call and was the one path the retry change missed

## 3. Stop the history tool overruling dedup

- [x] 3.1 Re-scope HISTORY CHECK: annotations decide new vs continuation; a keyword match alone may not demote or skip a story
- [x] 3.2 State that an article with no `[FOLLOW-UP: ...]` header is new, and that prior coverage may only be asserted from the header
- [x] 3.3 Record why the tool's recall must not outrank dedup's comparison

## 4. Tests

- [x] 4.1 `saveDedupResults` persists the follow-up context; a null context stays null
- [x] 4.2 `findLinkedArticlesAndTopics` returns the persisted annotations
- [x] 4.3 A regeneration passes the source episode's annotations to the composer
- [x] 4.4 A regenerated episode re-persists the annotations it composed with
- [x] 4.5 The prompt asserts annotation primacy and is present in all three composers
- [x] 4.6 Migration test: the column exists and defaults to null on existing rows
- [x] 4.7 Widen the three `excludes follow-up annotation when empty` assertions: the prompt now names the marker in its own rules
- [x] 4.8 Run `mvn test` and confirm the whole suite passes

## 5. Verify against the real pipeline

- [x] 5.1 Restart, regenerate today's episode, and confirm it leads with the launch
