## Context

Article selection already runs on the episode's stored window (see `episode-article-window`), so the
run knows exactly which period it is about by the time it reaches compose. The compose stage was
never given that period, and three things inside it read the clock instead: the prompt's `Date:`
line (`buildCurrentDate`), the Friday humor beat (`buildHumorBlock`), and the variety picker's key.

Two properties of the existing code shape the approach.

- The window is an `EpisodeWindow` of instants, deliberately timezone-free, while the prompt needs a
  calendar date. Turning one into the other requires the podcast's timezone, which
  `EpisodeWindowResolver.zoneOf` already owns and shares with the scheduler.
- `LlmPipeline.compose` is the single chokepoint every generation path goes through, and
  `recompose` is the one the regeneration path uses. Both already receive the podcast; neither
  received the window.

## Goals / Non-Goals

**Goals:**

- A script states the day it is about, so a re-run or a regeneration of a past day is truthful.
- The Friday beat and the variety rotation follow the episode's day, making the rotation the pure
  function of `(podcastId, episodeDate)` its spec already claims it is.

**Non-Goals:**

- Changing `generatedAt`, the feed item title or the show notes. Those describe when the episode was
  produced and published, not which day it covers, and a re-run deliberately keeps its own
  `generatedAt`.

## Decisions

**The episode's day is the date its window ends, in the podcast's timezone.** The window ends at the
scheduled slot being served, which is the slot the episode is published for, so its date is the day
the episode is about. Reading it in the podcast's timezone matters at the edges: a slot at 00:30 in
Amsterdam is the previous date in UTC, and the listener's day is the podcast's, not the server's.
`EpisodeWindowResolver.episodeDateOf` sits next to `zoneOf` and `windowOf` so all three read the
window the same way, including the UTC fallback for an unparseable timezone.

**The date travels in a `ComposeContext` alongside the rest of the prompt's inputs.** The composers
already took a tail of three optional arguments (`ttsScriptGuidelines`, `followUpAnnotations`,
`topicLabels`) on three overloads each, and the date is the fourth thing the prompt needs to know
about the run rather than about the articles. One data class carries all four, so
`LlmPipeline.compose` and `recompose` are back to four parameters, a call site names what it sets,
and the next prompt input costs no signature change. The pipeline resolves the TTS guidelines from
the podcast's provider and fills them into the context it was handed, which is why that field is
part of the same object rather than a separate argument.

Each field keeps a default, so a test asserting on the article block states nothing about dates or
topics. A caller that omits `episodeDate` therefore still gets today, which is right for a preview
and wrong for a past window: every real path passes it, and the composer tests fail if a prompt
states a day other than the episode's.

**A preview uses the window that ends now.** A preview serves no stored episode and is a look at
what the next episode would say, so the current slot is the honest day for it.

## Risks / Trade-offs

- **A default that silently falls back to today.** A future call site can build a `ComposeContext`
  without an `episodeDate` and get the old behaviour without a compile error. The default lives in
  one place now, where it is documented next to what it decides, and the composer tests fail if a
  prompt states a day other than the episode's.
- **A cron at midnight.** A window ending at 00:00 belongs to the day that starts at that instant,
  so an episode whose slot is midnight announces the day whose content it is about to cover rather
  than the day it summarises. No podcast is scheduled that way today, and the alternative (dating
  the episode from the window's start) would misdate every daytime slot.
