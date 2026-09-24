## Why

On the Upcoming Episode page the Focus input sat in the header next to the Generate Episode button, taking up header space for an optional field that only matters at the moment an episode is generated. The confirmation dialog also said little about what a regular or focus episode actually does, including how a focus changes the intro and closing.

## What Changes

- The Focus input moves from the page header into the confirmation dialog opened by Generate Episode, with a "Focus (optional)" label. It has keyboard focus when the dialog opens.
- The dialog offers a Regular | Focus selection (Regular by default). The Focus input shows only for Focus, and generating a focus episode requires a non-empty focus. The explanation follows the selection:
  - Regular: articles scoring at the relevance threshold or above against the podcast topic, grouped with duplicates merged; research as the deep-dive setting says; built from the upcoming articles, which are marked as used; the schedule moves on; the intro and sign-off follow the podcast's usual format; review follows the podcast's `requireReview` setting.
  - Focus: every article in the window, including used ones (pure retweets excluded), is scored against the focus and only those at the threshold or above are kept; web search always runs with up to 5 queries across the subject's angles, plus past coverage; the intro announces an extra, special episode and the closing says the regular episode follows as usual; the feed title reads "Special: <focus>"; it always stops for review and uses up no articles.
- Pressing Enter in the input generates, like the dialog's Generate button.
- The request sent to the API is unchanged.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. This is a layout change with no requirement change (`skip_specs: true`).

## Impact

- `frontend/src/app/podcasts/[podcastId]/upcoming/page.tsx` only. No API changes.
