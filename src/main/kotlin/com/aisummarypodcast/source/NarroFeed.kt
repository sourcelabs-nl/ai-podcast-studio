package com.aisummarypodcast.source

/**
 * Quirks of the Narro feed format, kept out of the generic [RssFeedFetcher].
 *
 * Narro merges many X accounts into one RSS document, and marks a reply by wrapping the account
 * being replied to in a `narro-reply-header` span rather than by any title convention. The rest of
 * the pipeline recognises Nitter's `R to @handle: ` title prefix, so the fetcher translates the
 * marker into that form and nothing downstream needs to know Narro exists.
 */
object NarroFeed {

    // The entry HTML arrives entity-unescaped from the feed parser, so the class attribute is
    // matched as literal markup. The handle may or may not carry its leading @.
    private val REPLY_HEADER = Regex(
        """narro-reply-header["'][^>]*>\s*@?([A-Za-z0-9_]{1,15})""",
        RegexOption.IGNORE_CASE
    )

    /** The account an entry replies to, or null when the entry is not a marked reply. */
    fun replyTarget(rawHtml: String): String? =
        REPLY_HEADER.find(rawHtml)?.groupValues?.get(1)
}
