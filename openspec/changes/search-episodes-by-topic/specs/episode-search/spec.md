## ADDED Requirements

### Requirement: Keyword search over a podcast's episodes
The episode list endpoint `GET /users/{userId}/podcasts/{podcastId}/episodes` SHALL accept an optional `q` query parameter that restricts the results to episodes matching the keyword.

`q` SHALL compose with the existing `status` filter and with pagination: an episode is returned only when it satisfies both the status filter and the search, and `total` SHALL report the number of matching episodes rather than the number of episodes in the podcast. Result ordering SHALL remain the list's existing `generated_at DESC, id DESC`.

A `q` that is absent, blank, or shorter than 2 characters after trimming SHALL be ignored, so the endpoint behaves exactly as it does today. Leading and trailing whitespace SHALL be trimmed before matching.

#### Scenario: Search restricts the returned episodes
- **WHEN** `GET .../episodes?q=retrieval` is called and 3 of the podcast's episodes match
- **THEN** the response contains those 3 episodes and `total` is 3

#### Scenario: Search composes with the status filter
- **WHEN** `GET .../episodes?q=retrieval&status=GENERATED` is called
- **THEN** only episodes that both match the keyword and have status `GENERATED` are returned

#### Scenario: Search composes with pagination
- **WHEN** `GET .../episodes?q=agent&page=1&pageSize=10` is called and 25 episodes match
- **THEN** the second page of 10 matching episodes is returned, `total` is 25, and ordering stays newest first

#### Scenario: Blank query is ignored
- **WHEN** `GET .../episodes?q=` or `GET .../episodes?q=%20` is called
- **THEN** the response is identical to calling the endpoint with no `q` parameter

#### Scenario: Single-character query is ignored
- **WHEN** `GET .../episodes?q=a` is called
- **THEN** the response is identical to calling the endpoint with no `q` parameter

### Requirement: Search matches covered stories and episode text
A query term SHALL match case-insensitively as a substring of any of the following fields:
- `episode_articles.topic`, for links belonging to the episode
- `articles.title`, for articles linked to the episode
- the episode's own `script_text`, `recap`, and `show_notes`

Article and topic matching SHALL be restricted to stories that were covered in the episode, identified by `episode_articles.topic_order IS NOT NULL`. Articles that were gathered and scored for the episode but never composed into the script SHALL NOT be searched, so an episode only matches on content a listener would have heard.

A covered article SHALL match on its `title`, its `summary`, or its `body`. All three are required: an article's subject frequently appears only in its text, so matching the headline alone reports an episode as "mentioned in the script only" when its source material plainly discusses the term. An article matching only in its text SHALL still be reported by its title, which is the only label a list row can show.

A query SHALL be split on whitespace into terms, and an episode matches only when EVERY term matches at least one of the fields above. The terms need not match the same field, and need not be adjacent in any field.

A term SHALL match whole words only: the characters immediately before and after an occurrence must not be letters. Substring matching is not acceptable here, because "Java" then matches "JavaScript", which on a real archive accounted for the majority of that query's hits. Digits SHALL NOT count as word characters for this purpose, so "qwen" still matches "Qwen3.8", where a model name runs straight into its version. A term at the very start or end of a field SHALL match, and adjacent punctuation SHALL NOT prevent a match. Characters that the matching engine treats as pattern metacharacters SHALL be matched literally, so a query containing one cannot behave as a wildcard.

#### Scenario: Match on a topic label
- **WHEN** an episode has a covered story with topic `Recall trap in retriever configuration for code repair` and the query is `retriever`
- **THEN** the episode matches

#### Scenario: Match on an article title
- **WHEN** an episode has a covered article titled `RAG is not dead, it just moved` and the query is `rag`
- **THEN** the episode matches

#### Scenario: Match on script text only
- **WHEN** the phrase appears in the episode's `script_text` but in none of its covered topics or article titles
- **THEN** the episode matches

#### Scenario: Match is case-insensitive
- **WHEN** the query is `QWEN` and a topic contains `Qwen`
- **THEN** the episode matches

#### Scenario: Uncovered article does not match
- **WHEN** an article linked to the episode has `topic_order IS NULL` and its title is the only place the term appears
- **THEN** the episode does NOT match

#### Scenario: Every term must match
- **WHEN** the query is `retrieval augmented` and an episode's searchable fields contain `retrieval` but nowhere contain `augmented`
- **THEN** the episode does NOT match

#### Scenario: Terms may match different fields
- **WHEN** the query is `qwen benchmark`, one covered topic contains `Qwen` and the script text contains `benchmark`
- **THEN** the episode matches

#### Scenario: Pattern metacharacters are matched literally
- **WHEN** the query is `*`
- **THEN** only episodes whose searchable text actually contains an asterisk match, rather than every episode

#### Scenario: A term does not match inside a longer word
- **WHEN** the query is `java` and an episode mentions only `JavaScript`
- **THEN** the episode does NOT match, while a query of `javascript` does find it

#### Scenario: A digit does not end a word
- **WHEN** the query is `qwen` and a covered topic reads `Qwen3.8-27B model release`
- **THEN** the episode matches, because the digit following the term is treated as a boundary

#### Scenario: A term at the edge of a field matches
- **WHEN** the term is the first or last word of the script
- **THEN** the episode matches

#### Scenario: Match on an article summary or body
- **WHEN** the term appears only in a covered article's summary or body, and not in its title or topic
- **THEN** the episode matches, and that article is reported by its title rather than the episode being called script-only

### Requirement: Search results report why each episode matched
When `q` is applied, each returned episode SHALL carry a `matches` object describing the hit, so the caller can show why the episode was returned without issuing a follow-up request per result. The object SHALL contain:
- `topics`: the distinct topic labels of the episode's covered stories that matched the query
- `articleTitles`: the titles of the episode's covered articles that matched the query
- `scriptOnly`: true when the episode matched but neither `topics` nor `articleTitles` has any entry, meaning the hit came only from `script_text`, `recap`, or `show_notes`
- `topicTotal` and `articleTotal`: how many topics and articles matched in total, counting those beyond the labels carried
- `scriptContext`: the text around the earliest term occurrence in the episode's `script_text`, `recap`, or `show_notes`, or absent when none of them mention it

`scriptContext` SHALL be taken from the first of `script_text`, `recap`, `show_notes` that contains a term, so the spoken script wins over a recap repeating it. Speaker tags such as `<expert>` SHALL be removed and whitespace collapsed, since they are not spoken words. The snippet SHALL keep bounded context on either side of the term, SHALL be elided with an ellipsis where it was cut, and SHALL NOT begin or end mid-word. It SHALL be provided whether or not topics or articles also matched, because seeing the sentence is useful even when the match is otherwise attributable.

A topic or title SHALL be listed when it contains ANY query term, not all of them. The episode as a whole has already been gated on every term, so requiring each individual label to contain all of them would hide the very topic that explains a multi-term match.

Each list SHALL be capped at 3 entries to bound the response size, while the totals count every match so the caller can summarise the remainder rather than implying the episode matched only what it shows. Each label SHALL have its whitespace collapsed and SHALL be truncated to 80 characters with a trailing ellipsis, because an article title can run to thousands of characters and the caller renders these on one line. When `q` is not applied, the `matches` object SHALL be absent, leaving the existing response shape unchanged.

#### Scenario: Topic match reported
- **WHEN** an episode matches on two covered topics
- **THEN** its `matches.topics` lists both labels and `matches.scriptOnly` is false

#### Scenario: Script-only match reported
- **WHEN** an episode matches only through its script text
- **THEN** `matches.topics` and `matches.articleTitles` are both empty, `matches.scriptOnly` is true, and `matches.scriptContext` carries the surrounding spoken text

#### Scenario: Script context accompanies an attributable match
- **WHEN** an episode matches on a covered topic AND its script also mentions the term
- **THEN** `matches.scriptOnly` is false and `matches.scriptContext` is still present

#### Scenario: No script context when only an article matched
- **WHEN** the term appears in a covered article but nowhere in the episode's script, recap, or show notes
- **THEN** `matches.scriptContext` is absent

#### Scenario: Match lists are capped while the total is exact
- **WHEN** an episode matches on 9 covered topics
- **THEN** `matches.topics` contains 3 entries and `matches.topicTotal` is 9

#### Scenario: A long label is truncated
- **WHEN** a matching article title is 3000 characters long
- **THEN** the reported label is collapsed to a single line and truncated to 120 characters with a trailing ellipsis

#### Scenario: A label matching one term of several is listed
- **WHEN** the query is `qwen rust`, one covered topic contains only `Qwen`, and the script supplies `rust`
- **THEN** that topic is listed in `matches.topics`

#### Scenario: No matches object without a query
- **WHEN** the endpoint is called without `q`
- **THEN** the returned episodes have no `matches` field and the response is byte-identical to today's
