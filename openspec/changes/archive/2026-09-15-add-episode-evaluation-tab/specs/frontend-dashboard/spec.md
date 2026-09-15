## ADDED Requirements

### Requirement: Episode evaluation tab
The episode detail page SHALL provide an Evaluation tab beside Script, Articles, Publications and Costs. The tab SHALL read the episode's stored evaluation data from `GET /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/scores`, `/metrics` and `/evaluation-runs`, and SHALL NOT trigger a scoring run.

The tab SHALL report, in four sections:

- **Attention score:** per stored score row, the overall score and the cliffhanger, humor and teaser component scores, each shown with the counts it was derived from (promises, deferred promises, unpaid promises, median deferral turns; humor beats, speaker balance, reaction ratio; distinct teaser topics). Each row SHALL be labelled with its judge model and scorer version, and rows from different scorers SHALL NOT be averaged together.
- **Script shape:** turn count, total words, and per role the turns held, the words held, the word share, the median and maximum turn length, and the number of turns over the compose prompt's sentence cap; plus laugh tags per role.
- **Anchors and outliers:** the judge's promises with the turn that makes each one and the turn that pays it off (or that it is unpaid), the judge's humor beats with their role and whether each reacts to the previous turn, and the metrics' backchannel candidates and same-speaker runs.
- **Run conditions:** per evaluation run, the prompt hash, variety selection, compose model, temperature, whether the cache was bypassed, whether it hit, and the tools that fired.

Figures the underlying data documents as proxies SHALL be labelled as such in the UI: laugh tags are a proxy for humor distribution and not a humor count, and a backchannel candidate is a turn shaped like a token of listening, not a defect.

#### Scenario: Evaluation tab shows a scored episode
- **WHEN** a user opens the Evaluation tab for an episode that has a stored score
- **THEN** the overall score and its three components are shown with their counts, labelled with the judge model and scorer version

#### Scenario: Script shape is reported per role
- **WHEN** the Evaluation tab is open for a multi-speaker episode
- **THEN** each role's turns, words, word share, median and maximum turn length, and turns over the sentence cap are shown

#### Scenario: Episode that was never scored
- **WHEN** a user opens the Evaluation tab for an episode with no stored score
- **THEN** the attention score section states that the episode has not been scored, and the other sections still render

#### Scenario: Episode with no evaluation runs
- **WHEN** a user opens the Evaluation tab for an episode with no evaluation run rows
- **THEN** the run conditions section states that no cache-bypassing run was recorded for this episode, distinctly from the not-scored state

#### Scenario: Multiple scorer versions
- **WHEN** an episode has score rows from more than one scorer version or judge model
- **THEN** the most recent row is shown expanded and the others are listed separately, never combined into one figure

#### Scenario: Tab does not trigger scoring
- **WHEN** the Evaluation tab is opened
- **THEN** only read requests are issued and no scoring run is started

### Requirement: Evaluation turn indices link into the script
Every turn index the Evaluation tab reports SHALL be a control that switches the page to the Script tab, scrolls that turn into view, and highlights it. This applies to judge promise and payoff turns, judge humor beats, backchannel candidates and same-speaker runs.

Turn indices SHALL be interpreted as 0-based positions over the same turn sequence the script viewer renders. When the parsed script has no turn at a reported index, that entry SHALL be shown without a jump control rather than scrolling to the wrong turn.

The highlight SHALL be cleared when the user switches tabs again.

#### Scenario: Jumping to a promise payoff
- **WHEN** a user clicks the payoff turn index of a promise in the Evaluation tab
- **THEN** the page switches to the Script tab, scrolls that turn into view and highlights it

#### Scenario: Jumping to a backchannel candidate
- **WHEN** a user clicks the turn index of a backchannel candidate
- **THEN** the page switches to the Script tab and highlights that turn

#### Scenario: Index outside the parsed script
- **WHEN** the tab reports a turn index that the parsed script does not contain
- **THEN** the entry is rendered without a jump control and no navigation happens

#### Scenario: Highlight does not persist
- **WHEN** a user jumps to a turn and then switches to another tab
- **THEN** the highlight is cleared

## MODIFIED Requirements

### Requirement: Bookmarkable tab links
All tabbed pages SHALL sync the active tab with a `?tab=X` URL query parameter. When the page loads with a valid `?tab` parameter, that tab SHALL be selected. When the user switches tabs, the URL SHALL be updated using `router.replace` (no new history entry). When no `?tab` parameter is present or the value is invalid, the default tab SHALL be selected.

#### Scenario: Tab from URL on page load
- **WHEN** a user navigates to `/podcasts/{podcastId}?tab=sources`
- **THEN** the Sources tab is active on load

#### Scenario: Tab updated in URL on switch
- **WHEN** a user clicks the "Publications" tab on the podcast detail page
- **THEN** the URL updates to `/podcasts/{podcastId}?tab=publications` without a full page reload and without adding a browser history entry

#### Scenario: Invalid tab parameter falls back to default
- **WHEN** a user navigates to `/podcasts/{podcastId}?tab=bogus`
- **THEN** the default tab (episodes) is selected

#### Scenario: No tab parameter uses default
- **WHEN** a user navigates to `/podcasts/{podcastId}` without a `?tab` parameter
- **THEN** the default tab (episodes) is selected

#### Scenario: Evaluation tab is bookmarkable
- **WHEN** a user navigates to `/podcasts/{podcastId}/episodes/{episodeId}?tab=evaluation`
- **THEN** the Evaluation tab is active on load

#### Scenario: All tabbed pages support bookmarkable tabs
- **WHEN** any of the 4 tabbed pages is loaded
- **THEN** tab state is synced with `?tab` query parameter:
  - `/podcasts/{podcastId}` — episodes (default), publications, sources
  - `/podcasts/{podcastId}/settings` — general (default), llm, tts, content, publishing
  - `/podcasts/{podcastId}/upcoming` — articles (default), script
  - `/podcasts/{podcastId}/episodes/{episodeId}` — script (default), articles, publications, costs, evaluation
