## Why

On the Upcoming Episode page the Focus input sat in the header next to the Generate Episode button, taking up header space for an optional field that only matters at the moment an episode is generated. The confirmation dialog also said little about what a regular or focus episode actually does, including how a focus changes the intro and closing.

## What Changes

- The Focus input moves from the page header into the confirmation dialog opened by Generate Episode, with a "Focus (optional)" label. It has keyboard focus when the dialog opens.
- The dialog explains what each kind of episode does, and switches between the two as the focus is typed:
  - Regular: built from the upcoming articles, which are marked as used; the schedule moves on; the intro and sign-off follow the podcast's usual format; review follows the podcast's `requireReview` setting.
  - Focus: every article in the window is scored against the focus and research goes deep on it; the intro announces an extra, special episode and the closing says the regular episode follows as usual; the feed title reads "Special: <focus>"; it always stops for review and uses up no articles.
- Pressing Enter in the input generates, like the dialog's Generate button.
- The request sent to the API is unchanged.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. This is a layout change with no requirement change (`skip_specs: true`).

## Impact

- `frontend/src/app/podcasts/[podcastId]/upcoming/page.tsx` only. No API changes.
