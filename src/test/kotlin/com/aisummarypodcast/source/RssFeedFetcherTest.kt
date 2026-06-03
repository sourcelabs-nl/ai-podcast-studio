package com.aisummarypodcast.source

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.DeepFetchProperties
import com.aisummarypodcast.config.SourceProperties
import com.rometools.rome.io.SyndFeedInput
import com.sun.net.httpserver.HttpServer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.jsoup.Jsoup
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.StringReader
import java.net.InetSocketAddress

class RssFeedFetcherTest {

    private val contentFetcher = mockk<ArticleContentFetcher>()

    private fun buildFetcher(deepFetchEnabled: Boolean): RssFeedFetcher {
        val props = mockk<AppProperties>()
        every { props.source } returns SourceProperties(deepFetch = DeepFetchProperties(enabled = deepFetchEnabled))
        return RssFeedFetcher(contentFetcher, props)
    }

    // Deep-fetch disabled so existing assertions exercise the raw feed body only.
    private val fetcher: RssFeedFetcher get() = buildFetcher(deepFetchEnabled = false)

    private lateinit var server: HttpServer
    private var port: Int = 0

    @BeforeEach
    fun startServer() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        port = server.address.port
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.stop(0)
    }

    private fun serveRss(xml: String) {
        server.createContext("/feed") { exchange ->
            val bytes = xml.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/rss+xml")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    @Test
    fun `HTML tags are stripped from RSS entry content`() {
        val rssXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Test Feed</title>
                <item>
                  <title>Test Article</title>
                  <link>https://example.com/1</link>
                  <description><![CDATA[<p>Breaking <strong>news</strong> today.</p><a href="https://example.com">Read more</a>]]></description>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val feed = SyndFeedInput().build(StringReader(rssXml))
        val entry = feed.entries.first()
        val rawBody = entry.description.value
        val cleanBody = Jsoup.parse(rawBody).text()

        assertEquals("Breaking news today. Read more", cleanBody)
    }

    @Test
    fun `plain text RSS content is preserved unchanged`() {
        val rssXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Test Feed</title>
                <item>
                  <title>Test Article</title>
                  <link>https://example.com/1</link>
                  <description>Simple plain text description</description>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val feed = SyndFeedInput().build(StringReader(rssXml))
        val entry = feed.entries.first()
        val rawBody = entry.description.value
        val cleanBody = Jsoup.parse(rawBody).text()

        assertEquals("Simple plain text description", cleanBody)
    }

    @Test
    fun `author extracted from RSS entry author element`() {
        serveRss("""
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Test Feed</title>
                <item>
                  <title>Test Article</title>
                  <link>https://example.com/1</link>
                  <description>Some content</description>
                  <author>John Smith</author>
                </item>
              </channel>
            </rss>
        """.trimIndent())

        val articles = fetcher.fetch("http://localhost:$port/feed", "src-1", null)

        assertEquals(1, articles.size)
        assertEquals("John Smith", articles[0].author)
    }

    @Test
    fun `author extracted from dc creator when author element is absent`() {
        serveRss("""
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:dc="http://purl.org/dc/elements/1.1/">
              <channel>
                <title>Test Feed</title>
                <item>
                  <title>Test Article</title>
                  <link>https://example.com/1</link>
                  <description>Some content</description>
                  <dc:creator>Jane Doe</dc:creator>
                </item>
              </channel>
            </rss>
        """.trimIndent())

        val articles = fetcher.fetch("http://localhost:$port/feed", "src-1", null)

        assertEquals(1, articles.size)
        assertEquals("Jane Doe", articles[0].author)
    }

    @Test
    fun `author is null when no author information in RSS entry`() {
        serveRss("""
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Test Feed</title>
                <item>
                  <title>Test Article</title>
                  <link>https://example.com/1</link>
                  <description>Some content</description>
                </item>
              </channel>
            </rss>
        """.trimIndent())

        val articles = fetcher.fetch("http://localhost:$port/feed", "src-1", null)

        assertEquals(1, articles.size)
        assertNull(articles[0].author)
    }

    private fun rssWithCategories(vararg categories: String): String {
        val categoryTags = categories.joinToString("\n") { "                  <category>$it</category>" }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Test Feed</title>
                <item>
                  <title>Test Article</title>
                  <link>https://example.com/1</link>
                  <description>Some content</description>
$categoryTags
                </item>
              </channel>
            </rss>
        """.trimIndent()
    }

    @Test
    fun `category filter includes entry with matching category`() {
        serveRss(rssWithCategories("Kotlin", "Programming"))

        val results = fetcher.fetch("http://localhost:$port/feed", "src-1", null, "kotlin,AI")

        assertEquals(1, results.size)
    }

    @Test
    fun `category filter excludes entry with no matching category`() {
        serveRss(rssWithCategories("Sports", "Football"))

        val results = fetcher.fetch("http://localhost:$port/feed", "src-1", null, "kotlin,AI")

        assertTrue(results.isEmpty())
    }

    @Test
    fun `category filter passes entries with no categories`() {
        serveRss("""
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Test Feed</title>
                <item>
                  <title>Test Article</title>
                  <link>https://example.com/1</link>
                  <description>Some content</description>
                </item>
              </channel>
            </rss>
        """.trimIndent())

        val results = fetcher.fetch("http://localhost:$port/feed", "src-1", null, "kotlin,AI")

        assertEquals(1, results.size)
    }

    @Test
    fun `no category filter passes all entries`() {
        serveRss(rssWithCategories("Sports", "Football"))

        val results = fetcher.fetch("http://localhost:$port/feed", "src-1", null, null)

        assertEquals(1, results.size)
    }

    @Test
    fun `category filter uses case-insensitive contains matching`() {
        serveRss(rssWithCategories("Technology"))

        val results = fetcher.fetch("http://localhost:$port/feed", "src-1", null, "tech")

        assertEquals(1, results.size)
    }

    private fun rssWithLink(link: String, description: String = "Some content"): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0">
          <channel>
            <title>Test Feed</title>
            <item>
              <title>Test Article</title>
              <link>$link</link>
              <description>$description</description>
            </item>
          </channel>
        </rss>
    """.trimIndent()

    @Test
    fun `deep-fetch replaces feed body with richer extracted article`() {
        serveRss(rssWithLink("https://example.com/1", "Short feed summary."))
        val full = "A much longer full article body extracted from the linked page with real context."
        every { contentFetcher.fetchBody("https://example.com/1", any()) } returns full

        val results = buildFetcher(deepFetchEnabled = true).fetch("http://localhost:$port/feed", "src-1", null)

        assertEquals(1, results.size)
        assertEquals(full, results[0].body)
    }

    @Test
    fun `deep-fetch keeps feed body when extracted text is not richer`() {
        serveRss(rssWithLink("https://example.com/1", "This is the original feed summary text."))
        every { contentFetcher.fetchBody("https://example.com/1", any()) } returns "short"

        val results = buildFetcher(deepFetchEnabled = true).fetch("http://localhost:$port/feed", "src-1", null)

        assertEquals("This is the original feed summary text.", results[0].body)
    }

    @Test
    fun `deep-fetch falls back to feed body on fetch error`() {
        serveRss(rssWithLink("https://example.com/1", "Original feed summary."))
        every { contentFetcher.fetchBody("https://example.com/1", any()) } throws RuntimeException("timeout")

        val results = buildFetcher(deepFetchEnabled = true).fetch("http://localhost:$port/feed", "src-1", null)

        assertEquals("Original feed summary.", results[0].body)
    }

    @Test
    fun `deep-fetch skips configured hosts and keeps feed body`() {
        serveRss(rssWithLink("https://x.com/someuser/status/123", "Tweet text body."))

        val results = buildFetcher(deepFetchEnabled = true).fetch("http://localhost:$port/feed", "src-1", null)

        assertEquals("Tweet text body.", results[0].body)
        verify(exactly = 0) { contentFetcher.fetchBody(any(), any()) }
    }

    @Test
    fun `deep-fetch disabled never calls the content fetcher`() {
        serveRss(rssWithLink("https://example.com/1", "Feed summary only."))

        val results = buildFetcher(deepFetchEnabled = false).fetch("http://localhost:$port/feed", "src-1", null)

        assertEquals("Feed summary only.", results[0].body)
        verify(exactly = 0) { contentFetcher.fetchBody(any(), any()) }
    }
}
