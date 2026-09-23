package com.aisummarypodcast.podcast

import com.aisummarypodcast.llm.PreviewResult
import com.aisummarypodcast.llm.RunOverrides
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodePurpose
import com.aisummarypodcast.store.Podcast

/**
 * Why a pipeline run happens. It decides the progress reporting and the failure handling of the run,
 * and is recorded on the episode as its [EpisodePurpose]; a preview writes no episode.
 */
enum class RunPurpose {
    SCHEDULED,
    MANUAL,
    FOCUS,
    RETRY,
    RERUN,
    REGENERATE,
    RECOMPOSE,
    EXPERIMENT,
    PREVIEW;

    fun episodePurpose(): EpisodePurpose? = if (this == PREVIEW) null else EpisodePurpose.valueOf(name)
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

    /**
     * An experiment's result: the script is stored on the episode and the epilogue runs, but no
     * audio is generated, the episode is never published, its articles are not consumed and the
     * podcast's schedule does not move. Only an [RunInput.ArticleSet] run has this outcome.
     */
    data object Sandbox : RunOutcome
}

/**
 * One pipeline run. [episode] is the episode the run writes to, and is null only for a
 * [RunOutcome.Transient] run. [overrides] change the configuration the run gets from the app
 * defaults and the podcast (see `RunConfig`); a run with `bypassLlmCache` set is an evaluation run
 * (see [ComposeContext.bypassLlmCache]).
 */
data class RunSpec(
    val podcast: Podcast,
    val episode: Episode?,
    val purpose: RunPurpose,
    val input: RunInput,
    val resumePoint: ResumePoint,
    val outcome: RunOutcome,
    val overrides: RunOverrides? = null
) {
    init {
        require(episode != null || outcome is RunOutcome.Transient) { "Only a transient run has no episode" }
        require(outcome !is RunOutcome.Sandbox || input is RunInput.ArticleSet) { "Only an article-set run is sandboxed" }
        require((purpose == RunPurpose.PREVIEW) == (outcome is RunOutcome.Transient)) { "A preview run, and only a preview run, is transient" }
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
