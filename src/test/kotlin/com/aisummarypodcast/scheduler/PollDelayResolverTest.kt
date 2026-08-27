package com.aisummarypodcast.scheduler

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.BriefingProperties
import com.aisummarypodcast.config.EncryptionProperties
import com.aisummarypodcast.config.EpisodesProperties
import com.aisummarypodcast.config.FeedProperties
import com.aisummarypodcast.config.HostOverride
import com.aisummarypodcast.config.LlmProperties
import com.aisummarypodcast.config.SourceProperties
import com.aisummarypodcast.store.Source
import com.aisummarypodcast.store.SourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PollDelayResolverTest {

    private fun resolver(
        pollDelaySeconds: Map<String, Int> = emptyMap(),
        hostOverrides: Map<String, HostOverride> = emptyMap()
    ): PollDelayResolver {
        val appProperties = AppProperties(
            llm = LlmProperties(),
            briefing = BriefingProperties(),
            episodes = EpisodesProperties(),
            feed = FeedProperties(),
            encryption = EncryptionProperties(masterKey = "test-key"),
            source = SourceProperties(
                pollDelaySeconds = pollDelaySeconds,
                hostOverrides = hostOverrides
            )
        )
        return PollDelayResolver(appProperties)
    }

    private fun source(
        type: SourceType = SourceType.RSS,
        url: String = "https://nitter.net/user/rss",
        pollDelaySeconds: Int? = null
    ) = Source(id = "s1", podcastId = "p1", type = type, url = url, pollDelaySeconds = pollDelaySeconds)

    @Test
    fun `per-source pollDelaySeconds takes highest precedence`() {
        val r = resolver(
            pollDelaySeconds = mapOf("rss" to 1),
            hostOverrides = mapOf("nitter.net" to HostOverride(pollDelaySeconds = 3))
        )
        assertEquals(5, r.resolveDelaySeconds(source(pollDelaySeconds = 5)))
    }

    @Test
    fun `host override takes precedence over type default`() {
        val r = resolver(
            pollDelaySeconds = mapOf("rss" to 1),
            hostOverrides = mapOf("nitter.net" to HostOverride(pollDelaySeconds = 3))
        )
        assertEquals(3, r.resolveDelaySeconds(source()))
    }

    @Test
    fun `type default is used when no per-source or host override`() {
        val r = resolver(pollDelaySeconds = mapOf("website" to 2))
        assertEquals(2, r.resolveDelaySeconds(source(type = SourceType.WEBSITE, url = "https://example.com")))
    }

    @Test
    fun `returns 0 when nothing configured`() {
        val r = resolver()
        assertEquals(0, r.resolveDelaySeconds(source()))
    }

    @Test
    fun `unparseable URL falls through to type default`() {
        val r = resolver(pollDelaySeconds = mapOf("rss" to 4))
        assertEquals(4, r.resolveDelaySeconds(source(type = SourceType.RSS, url = "not a url")))
    }

    @Test
    fun `unparseable URL with no type default returns 0`() {
        val r = resolver()
        assertEquals(0, r.resolveDelaySeconds(source(type = SourceType.RSS, url = "not a url")))
    }


    @Test
    fun `host override for different host does not apply`() {
        val r = resolver(hostOverrides = mapOf("other.net" to HostOverride(pollDelaySeconds = 3)))
        assertEquals(0, r.resolveDelaySeconds(source()))
    }
}
