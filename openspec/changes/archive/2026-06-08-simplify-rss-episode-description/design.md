## Context

Spotify (and other podcast apps) render the RSS `content:encoded` episode description as flat text. The current format lists every article title as a link, grouped under topic headings, which collapses into a long, noisy wall of raw article/post titles. The full per-article list is already published on the episode's sources HTML page, which the description links to.

This is a retrofit design: the change is already implemented in `FeedGenerator.buildHtmlDescription`.

## Goals / Non-Goals

**Goals:**
- Keep the episode description scannable in podcast apps: show notes + a short list of topic names + the sources-page link.
- Avoid duplicating the full article list that the sources page already provides.

**Non-Goals:**
- Changing the sources HTML page (`EpisodeSourcesGenerator`) — it keeps the full per-article list grouped by topic.
- Changing the plain `<description>` element, show notes generation, or the feed structure.

## Decisions

- **Topic names as plain `<li>` items, not links.** Linking each topic to a representative article (the previous spec) still surfaces raw article URLs/titles in apps that flatten HTML. Plain names are unambiguous and the sources link covers navigation.
- **Distinct topics derived in feed code** via `articles.filter { topicOrder != null }.map { topic ?: "Other" }.distinct()`, relying on the existing repository ordering by `topicOrder`. No query or schema change needed.
- **Legacy no-topic fallback drops the inline article list.** Old episodes without topic data show only show notes + sources link, consistent with the new minimal style (user-confirmed).

## Risks / Trade-offs

- [Listeners lose in-description article links] → The "view all sources and show notes" link at the end of every description leads to the full list.
- [Already-published feeds change format on regeneration] → Acceptable; feeds are regenerated on every publish and the new format is strictly simpler.
