## Why

An episode is charged for the articles it used and not for the articles it
scored. `scoreStageCost` sums the articles surviving into the script, so a run
that scored 188 candidates and composed 121 of them reports the cost of 121. The
other 67 were scored against their full article body, which is the most
expensive input the pipeline sends, and that spend is attributed to nothing.

The money was spent to produce that episode. A candidate is scored because it
fell in that episode's window, it is judged against that episode's covered
topics, and it is dropped by that episode's dedup stage. Windows do not overlap:
`EpisodeWindowResolver` starts a window where the previous one ended, so an
article dropped today is outside tomorrow's window and never becomes a candidate
again. Each scored article therefore belongs to exactly one episode, and leaving
the dropped ones out understates what every episode cost.

The same gap exists before an episode is generated. The upcoming page lists
candidates that have already been scored and paid for and shows nothing about
what they cost.

Knowing which candidates were dropped is worth more than the total alone. It
separates what the already-covered gate excluded from what the clustering call
deduplicated, and it makes visible the spend on articles that were never going
to be used.

The same tab hides the gate itself. Costs are read from fixed columns on the
episode row, one group per stage, written when the episode is generated. The
gate has no such group, so `LlmPipeline` adds its reported charge to the dedup
stage's amount before that amount is stored: the episode's total is right, but
the gate cannot be told apart from the clustering call it relieves, and its
request count and tokens arrive nowhere at all. Latency does not have this
problem, because it reads the recorded requests and groups them by stage, which
is the same move this change makes for the score stage.

## What Changes

An episode records every article that stood as a candidate for it, with what
became of that candidate: used, excluded by the already-covered gate, dropped as
a duplicate, or cut by the compose cap. This is recorded beside the existing
article links rather than inside them, because those links define which articles
an episode covers and are read by show notes, the sources file, the recap and
the marking of articles as processed.

An episode's score stage is then costed over every candidate it scored rather
than over the survivors, and reports how many calls that was.

Scoring calls become attributable to an article. The recorded LLM request gains
the article it was issued for, which is what lets an episode's score-stage
latency be read at all: these calls are made when an article arrives, long
before the episode exists, so they carry no episode and never appear in an
episode's request list today.

The dedup gate becomes a stage of its own in the costs breakdown, persisted on
the episode like every other stage, and stops being added to the dedup stage's
amount. The dedup row then describes the clustering call alone, matching what
the latency tab already reports under the same two names. The gate is costed
from its own columns rather than from its recorded requests, because those
requests are deleted after 30 days while an episode keeps its costs forever, and
because the episode's aggregate is defined as the sum of its stage columns.

The upcoming page reports the same figure for the candidates currently standing,
computed the same way.

An existing episode's dedup amount keeps the gate folded into it and its gate row
stays empty, because which part of that amount the gate accounted for was never
recorded separately. Existing episodes keep their other figures. Which candidates they dropped was
never recorded and cannot be recovered, and a migration that guessed would put
invented numbers next to measured ones.

## Capabilities

### New Capabilities

- `episode-candidate-articles`: an episode records every article scored as a
  candidate for it and what became of that candidate.

### Modified Capabilities

- `cost-tracking`: the score stage is costed over every candidate scored for the
  episode, not only those that reached the script, and the dedup gate is costed
  as a stage of its own instead of being added to the dedup stage's amount.
- `llm-call-telemetry`: a recorded request names the article it was issued for
  where there is one, so requests made outside an episode can still be gathered
  per episode.
- `upcoming-articles-api`: the upcoming content response reports what has been
  spent scoring the candidates currently standing.
- `frontend-upcoming-episode`: the upcoming page gains a Costs tab.
- `episode-detail-page`: the costs breakdown distinguishes the candidates that
  reached the script from those that did not, and lists the dedup gate beside
  the stage it relieves.

## Impact

- Three migrations: a candidate table, an article column on `llm_calls`, and the
  dedup gate's cost columns on `episodes`.
- `LlmPipeline`, `EpisodeService`, `TopicDedupFilter`, `CoveredTopicGate`: the
  outcome of each candidate is carried out of the dedup stage and recorded, and
  the gate's charge is no longer added to the dedup stage's cost.
- `ChatClientFactory`, `CachingChatModel`: telemetry attribution becomes a
  parameter object carrying the episode and the article, rather than one more
  positional argument.
- `ArticleScoreSummarizer`: names the article it is scoring.
- `LlmCallLatencyService`, `PodcastService`, the episode costs API and two
  frontend tabs.
- No new LLM calls anywhere. Every figure here describes calls already made.
