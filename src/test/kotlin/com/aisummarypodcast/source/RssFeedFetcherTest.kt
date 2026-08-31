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
import org.junit.jupiter.api.assertThrows
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient
import java.io.StringReader
import java.net.InetSocketAddress

class RssFeedFetcherTest {

    private val contentFetcher = mockk<ArticleContentFetcher>()

    private fun buildFetcher(deepFetchEnabled: Boolean): RssFeedFetcher {
        val props = mockk<AppProperties>()
        every { props.source } returns SourceProperties(deepFetch = DeepFetchProperties(enabled = deepFetchEnabled))
        return RssFeedFetcher(contentFetcher, props, RestClient.builder())
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
    fun `titleless Mastodon entry gets a title derived from its body`() {
        // Mastodon (and the X-mirror instances built on it) omit <title> entirely. Nitter's RSS
        // carried one per tweet, so repointing an X source at a mirror silently yielded nothing
        // until the title was derived from the body.
        serveRss("""
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Test Feed</title>
                <item>
                  <link>https://zpravobot.news/@OpenAI/117163420291416057</link>
                  <description><![CDATA[<p>We worked with METR and Redwood Research to conduct a third-party assessment.</p>]]></description>
                </item>
              </channel>
            </rss>
        """.trimIndent())

        val articles = fetcher.fetch("http://localhost:$port/feed", "src-1", null)

        assertEquals(1, articles.size)
        assertEquals("We worked with METR and Redwood Research to conduct a third-party assessment.", articles[0].title)
    }

    @Test
    fun `long titleless body is truncated on a word boundary`() {
        val body = "Anthropic is releasing a technical report and accompanying blog post that reconstruct the agents activity and explain why safeguards failed"
        serveRss("""
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Test Feed</title>
                <item>
                  <link>https://example.com/1</link>
                  <description>$body</description>
                </item>
              </channel>
            </rss>
        """.trimIndent())

        val articles = fetcher.fetch("http://localhost:$port/feed", "src-1", null)

        assertEquals(1, articles.size)
        val title = articles[0].title
        assertTrue(title.length <= 101, "title was ${title.length} chars: $title")
        assertTrue(title.endsWith("…"), "expected an ellipsis, got: $title")
        assertTrue(body.startsWith(title.removeSuffix("…")), "title should be a prefix of the body: $title")
        assertTrue(!title.removeSuffix("…").endsWith(" "), "should cut on a word boundary: $title")
    }

    @Test
    fun `entry with neither title nor body is skipped`() {
        serveRss("""
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Test Feed</title>
                <item>
                  <link>https://example.com/1</link>
                  <description></description>
                </item>
              </channel>
            </rss>
        """.trimIndent())

        val articles = fetcher.fetch("http://localhost:$port/feed", "src-1", null)

        assertEquals(0, articles.size)
    }

    @Test
    fun `explicit title is preferred over the body`() {
        serveRss("""
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Test Feed</title>
                <item>
                  <title>Real Headline</title>
                  <link>https://example.com/1</link>
                  <description>A body that should not become the title.</description>
                </item>
              </channel>
            </rss>
        """.trimIndent())

        val articles = fetcher.fetch("http://localhost:$port/feed", "src-1", null)

        assertEquals("Real Headline", articles[0].title)
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

    private fun serveStatus(status: Int) {
        server.createContext("/feed") { exchange ->
            exchange.sendResponseHeaders(status, -1)
            exchange.close()
        }
    }

    // The HTTP status of a failed feed fetch must reach PollFailure.classify. Before feed retrieval
    // moved to RestClient these all surfaced as a bare IOException and classified as transient,
    // which silently disabled auto-disable for every RSS source.
    @Test
    fun `feed returning 403 raises a client error carrying the status`() {
        serveStatus(403)

        val e = assertThrows<HttpClientErrorException> {
            fetcher.fetch("http://localhost:$port/feed", "src-1", null)
        }

        assertEquals(403, e.statusCode.value())
        assertTrue(PollFailure.classify(e) is PollFailure.Permanent)
    }

    @Test
    fun `feed returning 404 classifies as permanent`() {
        serveStatus(404)

        val e = assertThrows<HttpClientErrorException> {
            fetcher.fetch("http://localhost:$port/feed", "src-1", null)
        }

        assertTrue(PollFailure.classify(e) is PollFailure.Permanent)
    }

    @Test
    fun `feed returning 500 classifies as transient`() {
        serveStatus(500)

        val e = assertThrows<HttpServerErrorException> {
            fetcher.fetch("http://localhost:$port/feed", "src-1", null)
        }

        assertTrue(PollFailure.classify(e) is PollFailure.Transient)
    }

    @Test
    fun `feed returning 429 classifies as transient`() {
        serveStatus(429)

        val e = assertThrows<HttpClientErrorException> {
            fetcher.fetch("http://localhost:$port/feed", "src-1", null)
        }

        assertTrue(PollFailure.classify(e) is PollFailure.Transient)
    }

    @Test
    fun `malformed feed body classifies as transient`() {
        serveRss("this is not xml at all")

        val e = assertThrows<Exception> {
            fetcher.fetch("http://localhost:$port/feed", "src-1", null)
        }

        assertTrue(PollFailure.classify(e) is PollFailure.Transient)
    }

    @Test
    fun `feed request identifies the application`() {
        val agents = mutableListOf<String>()
        server.createContext("/feed") { exchange ->
            agents.add(exchange.requestHeaders.getFirst("User-Agent") ?: "")
            val bytes = rssWithLink("https://example.com/1").toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/rss+xml")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        fetcher.fetch("http://localhost:$port/feed", "src-1", null)

        assertEquals(listOf(ArticleContentFetcher.USER_AGENT), agents)
    }

    // Feeds commonly sit behind a redirect (http->https, moved paths). XmlReader(URL) followed them
    // via HttpURLConnection, so the RestClient path has to as well or healthy feeds would break.
    @Test
    fun `feed served behind a redirect is followed`() {
        server.createContext("/feed") { exchange ->
            exchange.responseHeaders.add("Location", "http://localhost:$port/moved")
            exchange.sendResponseHeaders(301, -1)
            exchange.close()
        }
        server.createContext("/moved") { exchange ->
            val bytes = rssWithLink("https://example.com/1", "Redirected feed body.").toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/rss+xml")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        val results = fetcher.fetch("http://localhost:$port/feed", "src-1", null)

        assertEquals(1, results.size)
        assertEquals("Redirected feed body.", results[0].body)
    }

    @Test
    fun `non-UTF-8 feed is decoded using the declared charset`() {
        val xml = """
            <?xml version="1.0" encoding="ISO-8859-1"?>
            <rss version="2.0">
              <channel>
                <title>Test Feed</title>
                <item>
                  <title>Café résumé</title>
                  <link>https://example.com/1</link>
                  <description>Naïve façade</description>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        server.createContext("/feed") { exchange ->
            val bytes = xml.toByteArray(Charsets.ISO_8859_1)
            exchange.responseHeaders.add("Content-Type", "application/rss+xml; charset=ISO-8859-1")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        val results = fetcher.fetch("http://localhost:$port/feed", "src-1", null)

        assertEquals("Café résumé", results[0].title)
        assertEquals("Naïve façade", results[0].body)
    }

    // --- Narro: markup in titles, and marker-based replies ---------------------------------------

    /** A real item from the `X via Narro` feed, entity-escaped exactly as the feed serves it. */
    private val narroReplyXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:dc="http://purl.org/dc/elements/1.1/">
          <channel>
            <title>Daily Agentic AI Podcast</title>
            <item>
              <title>@ivanfioravanti: &lt;span class=&quot;narro-reply-header&quot;&gt;@Rawtrutholog&lt;/span&gt; LOL</title>
              <link>https://x.com/ivanfioravanti/status/2094481641963938134</link>
              <pubDate>Mon, 31 Aug 2026 17:45:11 GMT</pubDate>
              <dc:creator>Ivan Fioravanti</dc:creator>
              <description>&lt;span class=&quot;narro-reply-header&quot;&gt;@Rawtrutholog&lt;/span&gt;
        &lt;span class=&quot;narro-reply-text&quot;&gt;quoted parent text&lt;/span&gt;

        @Rawtrutholog 😂 LOL!</description>
            </item>
          </channel>
        </rss>
    """.trimIndent()

    @Test
    fun `narro reply marker survives feed parsing and is detected`() {
        val feed = SyndFeedInput().build(StringReader(narroReplyXml))
        val rawBody = feed.entries.first().description.value

        // The feed escapes its markup; Rome hands it back unescaped, which is what the detector needs.
        assertTrue(rawBody.contains("narro-reply-header"))
        assertEquals("Rawtrutholog", NarroFeed.replyTarget(rawBody))
    }

    @Test
    fun `no reply target for an entry without the marker`() {
        assertNull(NarroFeed.replyTarget("<p>Just a normal post about agents.</p>"))
    }

    @Test
    fun `markup is stripped from an entry title and the reply is normalized`() {
        serveRss(narroReplyXml)

        val posts = fetcher.fetch("http://localhost:$port/feed", "s1", null)

        assertEquals(1, posts.size)
        val title = posts[0].title
        // The span markup that used to reach the article title, and the LLM prompts, is gone.
        assertTrue(title.none { it == '<' || it == '>' }, "title still contains markup: $title")
        assertTrue(title.startsWith("R to @Rawtrutholog: "), "title not normalized: $title")
        assertTrue(title.contains("@ivanfioravanti:"))
        assertTrue(title.contains("LOL"))
    }

    @Test
    fun `a non-reply entry title is not prefixed`() {
        assertEquals(
            "@steipete: A standalone thought",
            fetcher.normalizeReplyTitle("@steipete: A standalone thought", "<p>plain body</p>")
        )
    }

    @Test
    fun `an already-prefixed title is not prefixed twice`() {
        val title = "R to @someone: earlier reply"
        val body = """<span class="narro-reply-header">@another</span>"""

        val result = fetcher.normalizeReplyTitle(title, body)

        assertEquals(title, result)
        assertEquals(1, Regex("R to @").findAll(result).count())
    }
}
