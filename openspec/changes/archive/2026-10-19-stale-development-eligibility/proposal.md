# Proposal

## Why

A feed can publish an old development with a new date. On 17 October Developers Digest published a batch of guides, all dated `2026-10-17T00:00:00Z`, about developments from 1 to 28 September. They fell inside episode 296's window, were classified `DEVELOPMENT`, and a September change to Claude Code opened the episode as news. The pipeline knows when an article was published, but not when the development it reports happened.

## What Changes

- The scoring call also returns `developmentDate`: the date the content states the reported development happened, or null when the content states none. It is stored on the article.
- An article classified `DEVELOPMENT` whose `developmentDate` lies more than one day before the episode window's start is not eligible, and is logged with its title, URL and date. Retrospectives, unclassified articles and articles without a date are kept.

## Capabilities

### New Capabilities

### Modified Capabilities
- `llm-processing`: "Scoring places every article in time" also asks for the development date.
- `article-eligibility`: adds "Developments that happened before the window are not eligible".

## Impact

- `ArticleScoreSummarizer` (prompt, result), `Article` entity, new migration `V86__add_article_development_date.sql`, `ArticleEligibilityService`.
- Articles scored before this change carry no date and behave as before.
