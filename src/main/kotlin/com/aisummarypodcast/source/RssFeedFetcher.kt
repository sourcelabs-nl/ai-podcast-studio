package com.aisummarypodcast.source

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.store.Post
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URI
import java.time.Instant

@Component
class RssFeedFetcher(
    private val articleContentFetcher: ArticleContentFetcher,
    private val appProperties: AppProperties
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun fetch(url: String, sourceId: String, lastSeenId: String?, categoryFilter: String? = null, deepFetch: Boolean = true): List<Post> {
        val input = SyndFeedInput()
        @Suppress("DEPRECATION")
        val feed = input.build(XmlReader(URI(url).toURL()))
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
                val title = entry.title ?: return@mapNotNull null
                val rawBody = entry.contents.firstOrNull()?.value
                    ?: entry.description?.value
                    ?: return@mapNotNull null
                val feedBody = Jsoup.parse(rawBody).text()
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

    private fun isSkippedHost(link: String, skipHosts: List<String>): Boolean {
        val host = try {
            URI(link).host?.lowercase()
        } catch (_: Exception) {
            null
        } ?: return true // unparseable link: do not attempt a deep-fetch
        return skipHosts.any { host.contains(it) }
    }
}
