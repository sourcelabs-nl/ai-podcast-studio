package com.aisummarypodcast.podcast

import com.aisummarypodcast.llm.PreviewResult
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.Podcast

/** Why a pipeline run happens. It decides the progress reporting and the failure handling of the run. */
enum class RunPurpose {
    SCHEDULED,
    MANUAL,
    FOCUS,
    RETRY,
    RERUN,
    REGENERATE,
    RECOMPOSE,
    PREVIEW
}

/** What a run selects its articles from. */
sealed interface RunInput {
    /** The articles of [window], selected by the topic-scored regular path. */
    data class Window(val window: EpisodeWindow) : RunInput

    /** An existing episode's selected articles, recomposed without selecting again. */
    data class ArticleSet(val linked: LinkedArticlesResult, val window: EpisodeWindow) : RunInput

    /** The articles of [window] scored against [text] rather than the podcast's topic. */
    data class Focus(val text: String, val window: EpisodeWindow) : RunInput
}

/** What happens to a run's result. */
sealed interface RunOutcome {
    /**
     * The episode is finalized per the podcast's settings: sent to TTS, or stopped at review when the
     * podcast requires it or the episode is a focus episode. A regular episode marks its articles
     * consumed and, with [updateLastGenerated], advances the schedule. [generatedAt] keeps the
     * generation time of the episode a regeneration reproduces.
     */
    data class Deliver(
        val updateLastGenerated: Boolean = true,
        val generatedAt: String? = null
    ) : RunOutcome

    /**
     * The script is rewritten onto an episode under review with the reviewer's [feedback], and the
     * episode stays at `PENDING_REVIEW`.
     */
    data class Review(val feedback: String) : RunOutcome

    /**
     * The stages run and nothing about an episode is persisted. Stage progress goes to [onProgress]
     * only.
     */
    data class Transient(
        val onProgress: (stage: String, detail: Map<String, Any>) -> Unit = { _, _ -> }
    ) : RunOutcome
}

/**
 * One pipeline run. [episode] is the episode the run writes to, and is null only for a
 * [RunOutcome.Transient] run. [bypassLlmCache] makes the run an evaluation run (see
 * [ComposeContext.bypassLlmCache]).
 */
data class RunSpec(
    val podcast: Podcast,
    val episode: Episode?,
    val purpose: RunPurpose,
    val input: RunInput,
    val resumePoint: ResumePoint,
    val outcome: RunOutcome,
    val bypassLlmCache: Boolean = false
) {
    init {
        require(episode != null || outcome is RunOutcome.Transient) { "Only a transient run has no episode" }
    }
}

/** How a run ended. */
sealed interface RunResult {
    /** The run produced [episode] and applied its outcome to it. */
    data class Completed(val episode: Episode) : RunResult

    /** Nothing was left to compose, so the placeholder episode was deleted. */
    data object NothingToCompose : RunResult

    /** The run failed; [episode] is the failed episode, or null when the failure left no episode changed. */
    data class Failed(val episode: Episode?, val errorMessage: String?) : RunResult

    /** A transient run's script, or null when nothing was eligible. */
    data class Previewed(val preview: PreviewResult?) : RunResult
}
