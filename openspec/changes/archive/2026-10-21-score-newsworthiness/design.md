# Design

## One score, a better rubric

The score that already drives the threshold and the compose ranking now carries listener interest as well as topic fit. Existing consumers (threshold filter, ranking, API) need no change.

## Release floor and paper cap

A first rubric without a release floor and with a softer paper cap pushed a framework paper to 9 and a usable model release to 6. The shipped rubric adds the floor of 7 for usable releases and the cap of 6 for incremental framework, harness, method or benchmark papers. The measurements are in `knowledge/evals/newsworthiness-rubric-episode-299.md`.

## Threshold

The rubric alone does not reduce volume while the threshold is 5 and the compose cap is 40, because 22 to 27 candidates sit at 6. Volume falls only with the threshold at 7, so the podcast's threshold is raised to 7 alongside the prompt.

## Rejected: a separate newsworthiness field

A second score field would need a migration and a combined ranking over two numbers. That is more moving parts than changing what the existing score means.
