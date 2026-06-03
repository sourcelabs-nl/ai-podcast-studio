package com.aisummarypodcast.source

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

class ArticleContentFetcherTest {

    private val fetcher = ArticleContentFetcher(ContentExtractor())
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

    private fun serveHtml(html: String) {
        server.createContext("/article") { exchange ->
            val bytes = html.toByteArray()
            exchange.responseHeaders.add("Content-Type", "text/html")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    @Test
    fun `fetchBody extracts article text from the page`() {
        serveHtml(
            """
            <html><head><title>Title</title></head>
            <body>
              <nav>menu links</nav>
              <article><p>The full article body with the real content we want.</p></article>
              <footer>copyright</footer>
            </body></html>
            """.trimIndent()
        )

        val body = fetcher.fetchBody("http://localhost:$port/article", 10_000)

        assertTrue(body!!.contains("The full article body with the real content we want."))
        assertTrue(!body.contains("menu links"))
    }

    @Test
    fun `fetchBody returns null when page has no extractable text`() {
        serveHtml("<html><head></head><body></body></html>")

        val body = fetcher.fetchBody("http://localhost:$port/article", 10_000)

        assertNull(body)
    }
}
