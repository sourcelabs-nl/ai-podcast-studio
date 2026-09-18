package com.aisummarypodcast.llm

import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("com.aisummarypodcast.llm.NewsType")

/**
 * What an article is in time: whether it reports something that just happened, examines something
 * that already had, or describes something that simply exists.
 *
 * The scoring stage is the only place this can be decided. Compose sees an article's body only when
 * the run is small enough for `app.briefing.full-body-threshold`, which a 40-article day never is,
 * so for a normal episode the summary is the entire view downstream has of the article. Whatever
 * the summary drops about *when* something happened cannot be recovered later.
 *
 * Episode 222 named both failures this distinguishes. Its DeepSeek turn opened "DeepSeek released
 * V4.1 Flash" from a blog post whose own TL;DR began "When DeepSeek-V4.1 Flash was released, I
 * thought it might just be a post-training iteration... but after using it for a while": a
 * [RETROSPECTIVE] on a model the show had already covered the day before, restated as a fresh
 * release. Its OpenSpec turn was the openspec.dev landing page, an [EVERGREEN] page with no event
 * in it at all, read out as "sitting at around sixty eight thousand GitHub stars".
 */
enum class NewsType {
    /** A launch, release, announcement, publication, incident or result presented as new. */
    DEVELOPMENT,

    /**
     * Analysis, commentary, a benchmark or a deep dive about something already released or already
     * known. The analysis can be genuinely new; the thing it examines is not.
     */
    RETROSPECTIVE,

    /**
     * A landing page, README, documentation or marketing copy describing something that exists,
     * carrying no datable event. Its `publishedAt` is the moment someone linked it, never the
     * moment anything happened, so it passes a freshness window that means nothing for it.
     */
    EVERGREEN;

    companion object {
        /**
         * [raw] as a [NewsType], or null when the model answered with something else.
         *
         * Null is the "unknown" case throughout the pipeline and is always kept, matching how
         * [com.aisummarypodcast.llm.ArticleEligibilityService] treats an article with no
         * `publishedAt`: a classification we failed to obtain must not silently drop content.
         */
        fun parse(raw: String?): NewsType? {
            val value = raw?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
                ?: return null
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: null.also { log.warn("[LLM] Unrecognised newsType '{}' from the scoring model", value) }
        }
    }
}
