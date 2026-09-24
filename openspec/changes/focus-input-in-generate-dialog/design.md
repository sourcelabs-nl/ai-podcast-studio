## Context

The Generate Episode button already opens an `AlertDialog` whose text is derived from the `focus` state.

## Decisions

- **Reuse the existing confirmation dialog** instead of adding a second one, so generating stays one button and one confirmation.
- **Keep the focus value when the dialog is cancelled**, so reopening it does not lose what was typed.

## Risks / Trade-offs

- [Enter starts a paid generation] → same outcome as the Generate button in the same dialog, which is the confirmation step.
