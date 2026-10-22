# Design

## Compare against the saved podcast

The warning compares the subtopics of the loaded podcast with those of the form, so it appears while there are unsaved renames and disappears after a reload or a revert. Nothing is saved or changed by the warning itself.

## Names only

Only names matter: a weight change keeps the name, so scored articles still match their subtopic. An added name affects only articles scored from then on. The helper therefore returns the saved names that are absent from the edited map.

## Warn instead of reclassify

Reclassifying already-scored articles on save would cost an LLM call per article and change episode content without the user asking. The user chose to be told about the effect and to rescore deliberately.
