## Context

The three composers (`BriefingComposer`, `DialogueComposer`, `InterviewComposer`) assemble their prompts from shared block builders in `ComposerUtils.kt` (e.g. `buildPunctuationBlock()`), which are injected into a `Requirements:` / `Engagement techniques:` section. The rapid-fire segment is driven by `buildSubtopicPlanBlock` in `SubtopicPlan.kt`. Source ingestion runs through `SourcePoller`, which dispatches to `RssFeedFetcher` (RSS + YouTube), `WebsiteFetcher`, and `TwitterFetcher`. `WebsiteFetcher` already fetches a page with Jsoup and extracts text via `ContentExtractor`.

## Goals / Non-Goals

**Goals:**
- Make number-dense and complex stories digestible in audio via prompt rules shared across all styles.
- Stop the rapid-fire segment from counting items aloud.
- Give the pipeline full article context when feeds carry only a summary.

**Non-Goals:**
- No per-podcast toggles, DB schema, or API changes.
- No change to the upstream `ArticleScoreSummarizer` prompt.
- No replacement of the heuristic `ContentExtractor` with a dedicated readability library.

## Decisions

- **Shared prompt blocks over per-composer edits.** Add `buildNumbersBlock()` and `buildAudienceBlock()` to `ComposerUtils.kt` and inject them into all three composers, mirroring the existing `buildPunctuationBlock()` pattern. One source of truth, uniform wording. Alternative (editing each composer's prompt independently) rejected as duplicative and drift-prone.
- **Rapid-fire rule lives in `buildSubtopicPlanBlock`.** The "don't count aloud" and "one rounded number per item" instructions are appended to the existing rapid-fire closing instruction, keeping all rapid-fire guidance co-located.
- **Deep-fetch reuses `ContentExtractor` via a new `ArticleContentFetcher`.** A thin component does `Jsoup.connect(...).get()` + `ContentExtractor.extract(...)`, returning null on blank. `RssFeedFetcher` gains `appProperties` + `ArticleContentFetcher` dependencies and a `deepFetch` flag (default true; `SourcePoller` passes false for YouTube). Alternative (refactoring `WebsiteFetcher` to share one fetch path) deferred to avoid touching working code; the duplicated Jsoup connect line is trivial.
- **"Richer text wins" guard.** The extracted body replaces the feed body only when strictly longer, protecting against extraction returning less than a good `content:encoded` feed. Errors degrade to the feed body so a single bad page never fails a poll.
- **Host/source skip list.** Twitter/X/nitter and YouTube links are not scrapeable articles, so they are skipped by host substring (configurable) and YouTube polling disables deep-fetch outright.
- **Global config, on by default** (`app.source.deep-fetch`): `enabled`, `timeoutMs`, `skipHosts`. Matches how polling/age limits are configured.

## Risks / Trade-offs

- [Extra HTTP request per new RSS entry adds latency/load] → Entries within a feed usually point to different article hosts (so no single host is hammered), a 15s timeout bounds each fetch, and the existing per-host poll delays plus `skipHosts` provide levers if a specific host misbehaves.
- [Prompt rules can be over-applied, flattening genuinely important numbers] → Rules say "at most one number per claim" and "reserve depth for topics that warrant it" rather than banning numbers/elaboration; tuning is a prompt-only follow-up.
- [Content hash now reflects full article text] → Desired: dedup operates on richer content; computed downstream in `SourcePoller` from the resolved body, so no extra work needed.

## Migration Plan

Prompt-only and ingestion-only changes; no data migration. Deploy by restarting the app (rebuilds the jar). Rollback is reverting the change; `app.source.deep-fetch.enabled=false` disables deep-fetch without a code change.

## Open Questions

None.
