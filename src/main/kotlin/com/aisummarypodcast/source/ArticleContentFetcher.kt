package com.aisummarypodcast.source

import org.jsoup.Jsoup
import org.springframework.stereotype.Component

/**
 * Fetches the full article text behind a URL by retrieving the page and running it through
 * [ContentExtractor]. Used by [RssFeedFetcher] to "deep-fetch" the real article when a feed
 * only carries a short summary, so downstream LLM scoring and composition see full context.
 * Returns null when the page yields no usable text; network/parse errors propagate to the
 * caller, which decides whether to fall back to the feed summary.
 */
@Component
class ArticleContentFetcher(private val contentExtractor: ContentExtractor) {

    fun fetchBody(url: String, timeoutMs: Int): String? {
        val document = Jsoup.connect(url)
            .userAgent(USER_AGENT)
            .timeout(timeoutMs)
            .get()
        return contentExtractor.extract(document).takeIf { it.isNotBlank() }
    }

    companion object {
        const val USER_AGENT = "AiSummaryPodcast/1.0"
    }
}
