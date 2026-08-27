package com.aisummarypodcast.source

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.store.Post
import com.rometools.rome.feed.synd.SyndFeed
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.time.Instant

@Component
class RssFeedFetcher(
    private val articleContentFetcher: ArticleContentFetcher,
    private val appProperties: AppProperties,
    restClientBuilder: RestClient.Builder
) {

    private val log = LoggerFactory.getLogger(javaClass)

    // Redirects must be followed explicitly: the JDK HTTP client defaults to not following them,
    // whereas the HttpURLConnection this fetcher used to go through did. Plenty of feeds sit behind
    // a 301 (moved paths, http->https), and without this they fail on an empty body. NORMAL keeps
    // the old behaviour of refusing an HTTPS->HTTP downgrade.
    private val restClient = restClientBuilder
        .requestFactory(
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
            )
        )
        .build()

    fun fetch(url: String, sourceId: String, lastSeenId: String?, categoryFilter: String? = null, deepFetch: Boolean = true): List<Post> {
        val feed = readFeed(url)
        val lastSeenInstant = lastSeenId?.let { Instant.parse(it) }
        val filterTerms = categoryFilter?.split(",")?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }

        return feed.entries
            .filter { entry ->
                val publishedDate = entry.publishedDate ?: entry.updatedDate
                if (publishedDate == null) return@filter true
                lastSeenInstant == null || publishedDate.toInstant().isAfter(lastSeenInstant)
            }
            .filter { entry ->
                if (filterTerms.isNullOrEmpty()) return@filter true
                val categories = entry.categories.map { it.name }
                if (categories.isEmpty()) return@filter true
                categories.any { cat -> filterTerms.any { term -> cat.lowercase().contains(term) } }
            }
            .mapNotNull { entry ->
                val rawBody = entry.contents.firstOrNull()?.value
                    ?: entry.description?.value
                    ?: return@mapNotNull null
                val feedBody = Jsoup.parse(rawBody).text()
                val title = entry.title?.takeIf { it.isNotBlank() }
                    ?: deriveTitle(feedBody)
                    ?: return@mapNotNull null
                val link = entry.link ?: entry.uri ?: return@mapNotNull null
                val body = resolveBody(feedBody, link, deepFetch)
                val publishedAt = (entry.publishedDate ?: entry.updatedDate)?.toInstant()?.toString()
                val author = entry.author?.takeIf { it.isNotBlank() }
                    ?: entry.authors?.firstOrNull()?.name?.takeIf { it.isNotBlank() }

                Post(
                    sourceId = sourceId,
                    title = title,
                    body = body,
                    url = link,
                    publishedAt = publishedAt,
                    author = author,
                    contentHash = "",
                    createdAt = ""
                )
            }
            .also { log.info("Fetched {} new entries from RSS feed {}", it.size, url) }
    }

    /**
     * Builds a title from the entry body, for feeds whose items carry no `<title>`.
     *
     * Mastodon (and the X-mirror instances built on it) omit the element entirely: a post has no
     * title, only content. Without this, every such entry is dropped for want of a title and the
     * feed silently yields nothing, which is what happened when the X sources were repointed from
     * Nitter (whose RSS did carry a title per tweet) to a Mastodon mirror.
     *
     * Takes the first sentence when there is a clean break within [DERIVED_TITLE_MAX_LENGTH],
     * otherwise truncates on a word boundary and ellipsizes.
     */
    private fun deriveTitle(body: String): String? {
        val text = body.trim().replace(WHITESPACE_RUN, " ")
        if (text.isEmpty()) return null
        if (text.length <= DERIVED_TITLE_MAX_LENGTH) return text

        val head = text.take(DERIVED_TITLE_MAX_LENGTH)
        val sentenceEnd = head.lastIndexOfAny(charArrayOf('.', '!', '?'))
        if (sentenceEnd >= MIN_DERIVED_TITLE_LENGTH) return head.substring(0, sentenceEnd + 1).trim()

        val wordEnd = head.lastIndexOf(' ')
        val cut = if (wordEnd >= MIN_DERIVED_TITLE_LENGTH) head.substring(0, wordEnd) else head
        return "${cut.trimEnd()}…"
    }

    /**
     * Retrieves and parses the feed at [url].
     *
     * Deliberately goes through [RestClient] rather than `XmlReader(URL)`: the latter surfaces an
     * HTTP error as a bare `IOException` carrying the status only in its message, so every failed
     * poll of an RSS source classified as transient and neither auto-disable nor the host breaker
     * could ever see a permanent failure. `RestClient` raises status-bearing exceptions that
     * [PollFailure.classify] understands.
     *
     * The body is handed to [XmlReader] with the response's `Content-Type` so Rome performs the
     * same charset detection (header, then XML declaration) it did over the URL connection.
     */
    private fun readFeed(url: String): SyndFeed {
        val response = restClient.get()
            .uri(URI(url))
            .header(HttpHeaders.USER_AGENT, ArticleContentFetcher.USER_AGENT)
            .retrieve()
            .toEntity(ByteArray::class.java)

        val body = response.body ?: ByteArray(0)
        val contentType = response.headers.getFirst(HttpHeaders.CONTENT_TYPE)
        return XmlReader(ByteArrayInputStream(body), contentType, true).use { SyndFeedInput().build(it) }
    }

    /**
     * Returns the article body to store: the deep-fetched full text when deep-fetch is enabled,
     * the link is scrapeable, and the fetched text is richer than the feed body; otherwise the
     * feed body. Any fetch/parse failure degrades gracefully to the feed body.
     */
    private fun resolveBody(feedBody: String, link: String, deepFetch: Boolean): String {
        val config = appProperties.source.deepFetch
        if (!deepFetch || !config.enabled || isSkippedHost(link, config.skipHosts)) return feedBody
        return try {
            val fetched = articleContentFetcher.fetchBody(link, config.timeoutMs)
            if (fetched != null && fetched.length > feedBody.length) fetched else feedBody
        } catch (e: Exception) {
            log.warn("Deep-fetch failed for {}, falling back to feed summary: {}", link, e.message)
            feedBody
        }
    }

    companion object {
        /** Longest title synthesized from a body, chosen to read as a headline rather than a paragraph. */
        private const val DERIVED_TITLE_MAX_LENGTH = 100

        /** Below this, a sentence or word break is too short to be a useful title; truncate instead. */
        private const val MIN_DERIVED_TITLE_LENGTH = 20

        private val WHITESPACE_RUN = Regex("\\s+")
    }

    private fun isSkippedHost(link: String, skipHosts: List<String>): Boolean {
        val host = try {
            URI(link).host?.lowercase()
        } catch (_: Exception) {
            null
        } ?: return true // unparseable link: do not attempt a deep-fetch
        return skipHosts.any { host.contains(it) }
    }
}
