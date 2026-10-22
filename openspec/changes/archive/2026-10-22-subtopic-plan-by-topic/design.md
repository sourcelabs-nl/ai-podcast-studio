# Design

## Topic as the unit of bucketing

Dedup already decides which articles tell one story. `SubtopicPlan` follows that decision: given an article-id to topic map, all articles of a topic go to the bucket of the highest-weight subtopic among them. Ties use the bucket name order the plan already applies. A topic therefore appears in exactly one tier, so a story cannot be both a full segment and a rapid-fire item.

## One rapid-fire item per topic

The rapid-fire list has one entry per topic, labelled with the topic label, instead of one per article. Ranking is unchanged `(bucket weight DESC, relevance score DESC nulls last, id ASC)`, using the best article of the topic for score and id. The cap counts topics. Articles of a kept topic all stay in the episode.

## Skip what was already told

A topic can still be told earlier in the episode, for example when its cluster is in a full segment elsewhere in the model's opening. The rapid-fire block says to skip an item already covered earlier.

## Context wiring

`ComposeContext` gets an article-id to topic map next to `followUpAnnotations`. The pipeline fills it from the dedup results. On retry or recompose the dedup stage does not run, so the map is rebuilt from the topics stored on `episode_articles`. An empty map selects the per-article behaviour.
