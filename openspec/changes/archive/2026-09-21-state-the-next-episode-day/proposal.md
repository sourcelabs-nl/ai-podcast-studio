## Why

A compose-stage prompt states the episode's own date and nothing about the schedule. The sign-off
directives (`PromptVarietyDescriptors.describe(SignOffShape)`) ask for "a brief sign-off" and leave
the wording open, so when the sign-off promises a return the model has no source for the interval
and invents one.

Episode 221, on Wednesday 16 September, closed with "We'll be back next week". The podcast's cron is
`0 0 15 * * MON-FRI`: the next episode was the following morning. The promise was wrong by four
working days, in the last sentence a listener hears.

Nothing about this is model error in the usual sense. The cadence is knowable from
`Podcast.cron` and `Podcast.timezone`, which the prompt has never been shown. Every episode of
every weekday podcast is exposed to this, and the failure is silent: a wrong return promise reads
as fluent copy and only a listener who knows the schedule notices.

## What Changes

The compose-stage prompt states when the show is next on air, derived from the podcast's cron
rather than assumed.

The day is phrased the way a host says it: "tomorrow" when the next slot is the following day,
otherwise the weekday name. It is expressed relative to the episode's own day, not the day the run
happens, so a re-run or a regeneration of a past window promises what was true for that window.

The directive permits saying nothing about the next episode, and forbids naming any other interval.
A sign-off that makes no promise is correct; one that promises the wrong day is not, and the shape
directives already give the model four sign-off forms that need no return promise at all.

A cron the system cannot read, or one firing too infrequently to name a next day, yields no
directive at all rather than a guess. The model is then exactly as free as it was before, which is
the right failure mode: a missing line is better than a confident wrong date.

## Capabilities

### Modified Capabilities
- `compose-episode-date`: already owns which day a compose prompt is about and which run paths carry
  it. The next episode's day is the same quantity read forward from the same cron, derived by the
  same resolver, and carried on the same context object, so it belongs to this capability rather
  than to a new one.

## Impact

- **Code**: `EpisodeWindowResolver` gains a forward cron walk (`nextEpisodeDateAfter`) alongside the
  existing backward `previousSlot`. `ComposeContext` gains `nextEpisodeDate`. `ComposerUtils` gains
  `buildNextEpisodeBlock`. All three composers render it next to their `SIGN-OFF` directive.
  `LlmPipeline` fills it in at the three points where it already derives `ttsScriptGuidelines`.
- **Schema**: none.
- **Config**: none. The cron and timezone already exist per podcast.
- **Prompt size**: one bullet.
- **Not affected**: the episode's own date, the Friday humor beat and the prompt-variety rotation
  keep deriving from `episodeDate` exactly as before.
