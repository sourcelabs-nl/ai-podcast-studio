package com.aisummarypodcast.podcast

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.TopicGroupedArticle
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class EpisodeSourcesGeneratorTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var generator: EpisodeSourcesGenerator

    private val podcast = Podcast(id = "pod-1", userId = "user-1", name = "Tech Daily", topic = "tech")
    private val episode = Episode(
        id = 1,
        podcastId = "pod-1",
        generatedAt = "2026-03-12T10:00:00Z",
        scriptText = "Some script",
        recap = "Today we covered breaking news in tech.",
        audioFilePath = "${System.getProperty("java.io.tmpdir")}/pod-1/briefing-20260312-100000.mp3"
    )

    @BeforeEach
    fun setup() {
        val appProperties = mockk<AppProperties> {
            every { episodes } returns mockk {
                every { directory } returns tempDir.toString()
            }
        }
        generator = EpisodeSourcesGenerator(appProperties)
    }

    @Test
    fun `generates sources html with topic-grouped articles`() {
        val articles = listOf(
            TopicGroupedArticle("AI Safety Paper", "https://arxiv.org/ai-safety", "AI Safety", 0),
            TopicGroupedArticle("Safety Framework", "https://example.com/framework", "AI Safety", 0),
            TopicGroupedArticle("New Model Release", "https://example.com/model", "New Releases", 1)
        )

        val path = generator.generate(episode, podcast, articles)

        assertNotNull(path)
        val content = Files.readString(path!!)
        assertTrue(content.contains("<h2>Topics Covered</h2>"))
        assertTrue(content.contains("<h3>AI Safety</h3>"))
        assertTrue(content.contains("<h3>New Releases</h3>"))
        assertTrue(content.contains("<a href=\"https://arxiv.org/ai-safety\">AI Safety Paper</a>"))
        assertTrue(content.contains("<a href=\"https://example.com/model\">New Model Release</a>"))
        assertFalse(content.contains("<h2>Sources</h2>"))
    }

    @Test
    fun `generates flat list when no topic data`() {
        val articles = listOf(
            TopicGroupedArticle("Article One", "https://example.com/1", null, null),
            TopicGroupedArticle("Article Two", "https://example.com/2", null, null)
        )

        val path = generator.generate(episode, podcast, articles)

        assertNotNull(path)
        val content = Files.readString(path!!)
        assertTrue(content.contains("<h2>Sources</h2>"))
        assertFalse(content.contains("<h2>Topics Covered</h2>"))
        assertFalse(content.contains("<h3>"))
        assertTrue(content.contains("<a href=\"https://example.com/1\">Article One</a>"))
    }

    @Test
    fun `omits non-discussed articles and the Additional Sources section`() {
        val articles = listOf(
            TopicGroupedArticle("Grouped Article", "https://example.com/grouped", "AI Safety", 0),
            TopicGroupedArticle("Ungrouped Article", "https://example.com/ungrouped", "Code Quality", null)
        )

        val path = generator.generate(episode, podcast, articles)

        assertNotNull(path)
        val content = Files.readString(path!!)
        assertTrue(content.contains("<h2>Topics Covered</h2>"))
        assertTrue(content.contains("<h3>AI Safety</h3>"))
        assertTrue(content.contains("Grouped Article"))
        assertFalse(content.contains("<h2>Additional Sources</h2>"))
        assertFalse(content.contains("Background material"))
        assertFalse(content.contains("Ungrouped Article"), "Non-discussed articles should be omitted")
        assertFalse(content.contains("<h3>Code Quality</h3>"))
    }

    @Test
    fun `generates sources html with recap only`() {
        val path = generator.generate(episode, podcast, emptyList())

        assertNotNull(path)
        val content = Files.readString(path!!)
        assertTrue(content.contains("<h1>Tech Daily</h1>"))
        assertTrue(content.contains("Today we covered"))
        assertFalse(content.contains("<h2>Sources</h2>"))
        assertFalse(content.contains("<h2>Topics Covered</h2>"))
    }

    @Test
    fun `returns null when no recap and no articles`() {
        val noRecapEpisode = episode.copy(recap = null)
        assertNull(generator.generate(noRecapEpisode, podcast, emptyList()))
    }

    @Test
    fun `derives slug from audio file path`() {
        assertEquals("briefing-20260312-100000", generator.deriveSlug(episode))
    }

    @Test
    fun `derives slug from generatedAt when no audio path`() {
        val noAudioEpisode = episode.copy(audioFilePath = null)
        assertEquals("briefing-20260312-100000", generator.deriveSlug(noAudioEpisode))
    }

    @Test
    fun `file is written to correct path`() {
        val path = generator.generate(episode, podcast, emptyList())

        assertNotNull(path)
        assertTrue(path!!.toString().endsWith("briefing-20260312-100000-sources.html"))
        assertTrue(path.toString().contains("pod-1"))
    }

    @Test
    fun `topics are ordered by topic_order`() {
        // Articles are pre-ordered by topic_order ASC from the database query
        val articles = listOf(
            TopicGroupedArticle("First Topic Article", "https://example.com/1", "Topic A", 0),
            TopicGroupedArticle("Second Topic Article", "https://example.com/2", "Topic B", 1)
        )

        val path = generator.generate(episode, podcast, articles)

        val content = Files.readString(path!!)
        val topicAIndex = content.indexOf("<h3>Topic A</h3>")
        val topicBIndex = content.indexOf("<h3>Topic B</h3>")
        assertTrue(topicAIndex < topicBIndex, "Topic A (order 0) should appear before Topic B (order 1)")
    }

    @Test
    fun `truncates long article titles`() {
        val longTitle = "A".repeat(200)
        val articles = listOf(
            TopicGroupedArticle(longTitle, "https://example.com/long", null, null)
        )

        val path = generator.generate(episode, podcast, articles)

        val content = Files.readString(path!!)
        val expectedTruncated = "A".repeat(120) + "..."
        assertTrue(content.contains(expectedTruncated))
        assertFalse(content.contains(longTitle))
    }

    @Test
    fun `meta line shows duration, source count and listen link`() {
        val timedEpisode = episode.copy(durationSeconds = 754)
        val articles = listOf(
            TopicGroupedArticle("A", "https://example.com/a", "Topic A", 0),
            TopicGroupedArticle("B", "https://example.com/b", "Topic B", 1)
        )

        val content = Files.readString(generator.generate(timedEpisode, podcast, articles)!!)
        assertTrue(content.contains("class=\"meta\""), "Expected meta line")
        assertTrue(content.contains("12 min"), "Expected formatted duration")
        assertTrue(content.contains("2 sources"), "Expected source count")
        assertTrue(content.contains("&#9654; Listen"), "Expected listen link label")
        assertTrue(content.contains("href=\"briefing-20260312-100000.mp3\""), "Expected relative audio link")
    }

    @Test
    fun `source count reflects discussed articles when additional sources present`() {
        val articles = listOf(
            TopicGroupedArticle("Discussed", "https://example.com/d", "Topic A", 0),
            TopicGroupedArticle("Background", "https://example.com/bg", "Topic B", null)
        )

        val content = Files.readString(generator.generate(episode, podcast, articles)!!)
        assertTrue(content.contains("1 source"), "Expected only discussed article counted")
        assertFalse(content.contains("2 sources"), "Additional (non-discussed) sources should not be counted")
    }

    @Test
    fun `summary falls back to sanitized script when no recap or show notes`() {
        val taggedScript = "I have enough context. Writing the script now.\n\n" +
            "<interviewer>Welcome to today's episode.</interviewer><expert>Glad to be here.</expert>"
        val noRecapEpisode = episode.copy(recap = null, showNotes = null, scriptText = taggedScript)
        val articles = listOf(TopicGroupedArticle("A", "https://example.com/a", "Topic A", 0))

        val content = Files.readString(generator.generate(noRecapEpisode, podcast, articles)!!)
        assertTrue(content.contains("<h2>Summary</h2>"), "Expected a Summary section")
        assertTrue(content.contains("Welcome to today's episode."), "Expected spoken text in summary")
        assertFalse(content.contains("&lt;interviewer&gt;"), "Expected no speaker tags in summary")
        assertFalse(content.contains("Writing the script now"), "Expected leaked preamble stripped")
    }

    @Test
    fun `truncates title without splitting a surrogate pair at the boundary`() {
        // Emoji (a surrogate pair) positioned so its high half lands on the 120th UTF-16 unit.
        // Naive take(120) would leave a lone surrogate and crash strict UTF-8 file writing.
        val longTitle = "A".repeat(119) + "😀" + "B".repeat(80)
        val articles = listOf(
            TopicGroupedArticle(longTitle, "https://example.com/emoji", null, null)
        )

        val path = generator.generate(episode, podcast, articles)

        // Reading back proves the file was written as valid UTF-8 (no lone surrogate).
        val content = Files.readString(path!!)
        assertTrue(content.contains("A".repeat(119) + "..."), "Expected truncation just before the emoji")
        assertFalse(content.contains("�"), "Expected no replacement char (pair dropped cleanly, not mangled)")
    }

    @Test
    fun `does not truncate short article titles`() {
        val shortTitle = "Short Title"
        val articles = listOf(
            TopicGroupedArticle(shortTitle, "https://example.com/short", null, null)
        )

        val path = generator.generate(episode, podcast, articles)

        val content = Files.readString(path!!)
        assertTrue(content.contains(">Short Title</a>"))
    }
}
