package com.aisummarypodcast.store

/**
 * Why an episode's current script was produced: the purpose of the pipeline run that last wrote it
 * (see `RunPurpose`, which adds the never-persisted preview). [LEGACY] marks an episode that predates
 * the column and behaves like a scheduled or manual one everywhere. [EXPERIMENT] episodes are
 * excluded from every listing, the feed, schedule resolution, dedup history, active-episode checks
 * and auto-publish.
 */
enum class EpisodePurpose {
    SCHEDULED,
    MANUAL,
    FOCUS,
    RETRY,
    RERUN,
    REGENERATE,
    RECOMPOSE,
    EXPERIMENT,
    LEGACY
}
