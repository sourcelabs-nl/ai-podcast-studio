## Why

The episode sources page listed every linked article: the discussed topics under "Topics Covered" and the rest under an "Additional Sources" section. Now that topic coverage reflects only what the script discusses (see topics-covered-reflect-script), the "Additional Sources" section is a long list of background papers the episode never mentions, which is noise on the public page. The page should list only the sources behind topics actually covered.

## What Changes

- Remove the "Additional Sources" section from the generated sources page. Only articles whose topic was discussed (non-null `topic_order`) are listed, under "Topics Covered". Non-discussed articles are omitted from the page.
- The legacy flat "Sources" list (episodes with no topic data at all) is unchanged.

## Capabilities

### Modified Capabilities

- `episode-sources-file`: the page no longer renders an "Additional Sources" section; only discussed topics are listed.

## Impact

- `src/main/kotlin/com/aisummarypodcast/podcast/EpisodeSourcesGenerator.kt`
- `src/test/kotlin/com/aisummarypodcast/podcast/EpisodeSourcesGeneratorTest.kt`
- Existing episodes update on the next sources-file regeneration / publish.
