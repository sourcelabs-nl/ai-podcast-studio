## Context

See proposal.md (Why) for the three failures this change answers. Four properties of the existing
code shape the approach.

- The window was a derived value, computed inside `ArticleEligibilityService` from
  `findLatestPublishedByPodcastId(...).generatedAt`. Nothing recorded it, so no run could be
  reproduced and a failed day's articles silently rolled into the next episode.
- The episode row already exists before article selection: `createGeneratingEpisode` runs first and
  the pipeline stages follow. That makes the episode the natural owner of the window.
- `CronExpression` only walks forward. There is no `previous()`, so the previous slot has to be
  found by enumerating forward from a bounded floor.
- `sources.last_polled` is written on a failed poll as well as a successful one, so freshness alone
  cannot tell whether a source actually fetched anything.

## Goals / Non-Goals

**Goals:**

- Make a run own its input, so the same day can be run again and produce the same selection.
- Give a weekday episode a 24-hour window and a Monday episode the whole weekend, derived from the
  podcast's cron rather than from hardcoded weekdays.
- Stop composing an episode against a period the pollers have not fetched yet.

**Non-Goals:**

- Recording the candidate set on the episode. Selection still requires an article to be unused
  (`is_processed = 0`), which is what makes a published day non-reproducible until it is discarded.
  Making the run own its candidates as well is the next slice; it pulls in the cleanup and the
  `episode_articles` lifecycle.
- Changing retention. `app.source.max-article-age-days` (90) still bounds how far back a window can
  be reproduced.
- Weekday-specific logic. Monday's weekend reach is a consequence of the cron, not a rule about
  Mondays.

## Decisions

**The window lives on the episode, not on the podcast.** A podcast-level "covered until" pointer
would be one mutable cursor, which is exactly the shape that made the current behaviour
irreproducible: a discard or a failure has to move it, and then two runs disagree about what they
covered. A window per episode is a fact about that run, and where coverage ends is derived from
those facts by one query.

**A failed or discarded episode covers nothing.** `findLatestCoveredWindowEnd` excludes both
statuses, so the window of a day that has to be redone stays claimable. Coverage is derived from
episode rows rather than tracked separately, which means a discard needs no extra bookkeeping: the
status change alone re-opens the period.

**The window start is the previous cron slot, found by walking forward from the cap.** Enumerating
`cron.next()` from `windowEnd - maxWindowDays` and keeping the last slot before `windowEnd` is a few
iterations for any realistic cron, and it inherits the cron's own notion of which days count. The
alternative, subtracting a fixed 24 hours and special-casing Monday, would have to be taught about
every schedule the podcast might use and about public holidays it does not know.

**The gap extension is bounded by the same cap as the reach.** Without it, a podcast that failed for
three weeks would pull a three-week window into one episode. With it, the worst case is one capped
window and a WARN naming what was dropped.

**Instants, not strings.** The window is `Instant`, and article timestamps are parsed before
comparison. String comparison of ISO-8601 is only safe when every value is UTC with identical
precision, which is not true here: `published_at` comes from Rome (RSS), from Twitter and from the
aggregator. A tolerant parse keeps an article whose date cannot be read at all rather than dropping
it, because losing content silently is the failure mode this change exists to remove.

**Coverage is "polled within its own interval before the window end", not "polled after it".** The
literal reading would defer every episode by up to the largest poll interval, since a source polled
at 14:50 with a 30-minute interval has nothing outstanding at 15:00 but its timestamp still predates
the window end. Keying on the source's own interval separates a source that has nothing to fetch
from one that is genuinely behind. A source whose last poll failed counts as behind whatever its
timestamp says.

**The deferral creates nothing and touches nothing.** Because `createGeneratingEpisode` is what
moves `last_generated_at`, deferring before it leaves the slot due and the next 60-second tick
re-evaluates. No timer, no persisted "waiting" state, and a restart mid-wait simply re-checks.
Catching up is left to the normal polling loop and its backoff rather than forcing extra rounds: an
overdue source is polled within a minute anyway, and a source on backoff is on backoff for a reason.

**The deadline is measured from the slot, not from the first deferral.** A restart during the wait
would otherwise restart the clock and could defer indefinitely.

**A re-run creates a new episode rather than reviving the old one.** The source episode keeps its
status, its error and its publication history, and the re-run gets its own. Reviving a discarded row
would mean a published episode and its replacement sharing one identity.

## Risks / Trade-offs

- **A day that fails and is never re-run loses its content once the window has moved past it.** →
  The gap extension covers exactly this: the next window reaches back to where coverage ended, so
  the content lands in the following episode instead of being lost.
- **The coverage gate can delay an episode by up to the deadline.** → Bounded at 30 minutes by
  default and configurable; after that the episode is generated with a WARN naming the sources
  behind. An incomplete episode still beats no episode.
- **A source that is permanently broken makes every episode wait out the deadline.** → Visible as a
  recurring WARN naming that source, which is the signal to disable it. The
  `source-polling-backoff` capability already auto-disables a structurally dead source.
- **A published day cannot be re-run without discarding it first.** → Accepted for this slice, and
  the 409 says so explicitly. Discarding already resets the articles, which is what makes the
  re-run able to select them.
- **`resolveForNow` for a manual run mixes an ad-hoc window into the coverage chain.** → It ends at
  the current instant, so a later scheduled slot sees coverage ending mid-day and keeps its own
  previous-slot start. It never shortens a window; it can only prevent an unnecessary extension.

## Migration Plan

`V67__add_episode_window.sql` adds two nullable TEXT columns to `episodes`. Existing rows read as
having no window, which the resolver treats as "covered nothing", so the first window after
deployment falls back to the previous cron slot. No backfill: reconstructing the window of a past
episode would be a guess, and a wrong guess would suppress the gap extension. Deployment is a
restart. Rollback is reverting the code; the columns can stay behind harmlessly.
