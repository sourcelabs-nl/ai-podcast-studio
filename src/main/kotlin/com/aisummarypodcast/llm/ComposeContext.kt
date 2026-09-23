package com.aisummarypodcast.llm

import com.aisummarypodcast.research.PreComposeResearch
import java.time.LocalDate

/**
 * Everything a compose-stage prompt needs beyond the articles, the podcast and the model.
 *
 * These travel together through every compose path (a generation, a retry, a re-run, a
 * regeneration and a preview), and the set grows as the prompt learns about more of the run, so
 * they are one object rather than a tail of optional positional arguments.
 *
 * [ttsScriptGuidelines] is resolved by the pipeline from the podcast's TTS provider, so callers
 * upstream of it leave it empty and the pipeline fills it in.
 *
 * [episodeDate] is the day the episode is about, which is the date its article window ends read in
 * the podcast's timezone (see `EpisodeWindowResolver.episodeDateOf`). It decides the date the script
 * announces, whether the end-of-week humor beat applies, and the prompt-variety rotation, so a
 * re-run or a regeneration of a past day must pass that day rather than rely on the default.
 *
 * [nextEpisodeDate] is the day the show is next on air after [episodeDate], resolved by the pipeline
 * from the podcast's cron so the sign-off can promise the right day. Like [ttsScriptGuidelines] it
 * is derived rather than passed in, so callers leave it null and the pipeline fills it in. It stays
 * null when the cron is unparseable or fires too infrequently to name a next day.
 *
 * [runConfig] is the configuration of the run composing (see [RunConfig]): the models, reasoning
 * effort, provider preferences and target length the composers use. The pipeline fills it in for a
 * caller that passes none, from the podcast's own configuration.
 *
 * [bypassLlmCache] comes from [runConfig] and is set by an evaluation run comparing repetitions of
 * one prompt variant. The LLM cache keys on model and prompt text and ignores temperature, so
 * without it the second and later repetitions replay the first one's script and the comparison
 * measures nothing.
 *
 * [episodeId] is the episode being composed, used to attribute the recorded request telemetry. It
 * travels here rather than as a parameter on every composer because every compose path already
 * carries this object, and a preview has no episode to name, so it stays null.
 *
 * [focus] is the focus text of a focus episode, null for a regular episode. A focus episode always
 * runs web research at the raised query cap and tells the model what the episode is about.
 *
 * [extraInstruction] is a reviewer's feedback on the previous script of a focus episode, appended to
 * the prompt like the TTS guidelines are.
 *
 * [recentFocusEpisodes] are the focus episodes aired since the previous regular episode, so a regular
 * episode picks their topics up as a follow-up rather than repeating or ignoring them.
 *
 * [research] is what the pre-compose research stage found: web search results and past-episode
 * matches. Like [ttsScriptGuidelines] it is filled in by the pipeline, so callers leave it empty.
 */
data class ComposeContext(
    val ttsScriptGuidelines: String = "",
    val followUpAnnotations: Map<Long, String> = emptyMap(),
    val topicLabels: List<String> = emptyList(),
    val episodeDate: LocalDate = LocalDate.now(),
    val nextEpisodeDate: LocalDate? = null,
    val episodeId: Long? = null,
    val focus: String? = null,
    val extraInstruction: String? = null,
    val recentFocusEpisodes: List<RecentFocusEpisode> = emptyList(),
    val research: PreComposeResearch = PreComposeResearch.NONE,
    val runConfig: RunConfig? = null
) {
    val bypassLlmCache: Boolean get() = runConfig?.bypassLlmCache ?: false
}

/** A focus episode that aired since the previous regular episode, as the compose prompt names it. */
data class RecentFocusEpisode(
    val focus: String,
    val generatedAt: String
)
