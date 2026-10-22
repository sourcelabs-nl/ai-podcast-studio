# Proposal

## Why

Episode 299 (2026-10-20) read as a list of about 17 topics, mostly arXiv paper abstracts. `relevanceScore` measured only topic fit: of 70 eligible candidates, 36 scored 7 or higher and 23 of those were papers, and the compose cap of 40 sent nearly all of them to compose. Every on-topic paper scored high, so nothing separated what a listener would want to hear from what merely fits the topic.

## What Changes

- The scoring prompt in `ArticleScoreSummarizer` rates relevance on topic fit and listener interest together, with a 0-10 rubric: 9-10 a development people in the field will talk about, 7-8 a concrete consequence a listener could act on or retell, 5-6 incremental or narrow, 3-4 loosely related, 0-2 irrelevant.
- A release of a model, agent or developer product that people can use scores at least 7.
- A paper whose main contribution is a framework, harness, method or benchmark with incremental improvements scores at most 6. A paper reaches 7 or higher only with a surprising or broadly applicable measured finding.
- Together with the prompt, the relevance threshold of the podcast "The Daily Agentic AI Podcast" goes from 5 to 7. This is a per-podcast setting changed in the UI, not code.

## Capabilities

### New Capabilities

### Modified Capabilities
- `llm-processing`: adds "Relevance score reflects listener interest".

## Impact

- `ArticleScoreSummarizer` (prompt text only), no schema or response change.
- Tests: `ArticleScoreSummarizerTest`.
- Evidence and the rubric's measured effect: `knowledge/evals/newsworthiness-rubric-episode-299.md`.
