package com.aisummarypodcast.llm

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
 * [bypassLlmCache] is set by an evaluation run comparing repetitions of one prompt variant. The
 * LLM cache keys on model and prompt text and ignores temperature, so without it the second and
 * later repetitions replay the first one's script and the comparison measures nothing.
 */
data class ComposeContext(
    val ttsScriptGuidelines: String = "",
    val followUpAnnotations: Map<Long, String> = emptyMap(),
    val topicLabels: List<String> = emptyList(),
    val episodeDate: LocalDate = LocalDate.now(),
    val nextEpisodeDate: LocalDate? = null,
    val bypassLlmCache: Boolean = false
)
