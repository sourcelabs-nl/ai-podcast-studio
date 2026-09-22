package com.aisummarypodcast.llm

import com.aisummarypodcast.store.Article

/**
 * An article scored by one call, with the usage that call cost. For a focus episode [article] is an
 * in-memory copy carrying the relevance and summary judged against the focus text, which is never
 * written back onto the stored article.
 */
data class FocusScoredArticle(
    val article: Article,
    val usage: TokenUsage
)
