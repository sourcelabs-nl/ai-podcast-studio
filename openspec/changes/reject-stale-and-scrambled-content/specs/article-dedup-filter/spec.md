## ADDED Requirements

### Requirement: Dedup recalls topics, not only headlines

The dedup prompt SHALL carry the cluster topic labels of recent episodes alongside the historical
article titles, and SHALL treat that list as the authoritative record of what the podcast has
already said.

Titles alone are not sufficient recall. A topic enters an episode through whichever article happened
to be selected for it, and that article's headline may be about something else entirely: episode
221 covered a DeepSeek release under the label `DeepSeek v4.1 Flash vs GLM 5.3 Flash comparison`,
carried by a post headlined about the GLM comparison with "DeepSeek" nowhere in its title. Given
titles only there was nothing for the next day's dedup to match on, and the release was composed a
second time as fresh news.

A cluster matching a covered topic SHALL be `CONTINUATION` even when its articles are new, from a
different source, or differently headlined. A fresh analysis, technical report, benchmark or
follow-up concerning an already-covered release SHALL be a `CONTINUATION` rather than a `NEW`
release, and its `previousContext` SHALL say what was covered before.

When there are no covered topics the block SHALL be omitted, as the historical-articles block
already is.

#### Scenario: A new article on an already-covered topic is a continuation

- **WHEN** a candidate article analyses a model release that a recent episode already covered, under
  a different headline and from a different source
- **THEN** its cluster is `CONTINUATION` with a `previousContext` describing the earlier coverage,
  not a `NEW` release

#### Scenario: No covered topics yet

- **WHEN** no recent episode carries a topic label
- **THEN** the prompt omits the covered-topics section entirely
