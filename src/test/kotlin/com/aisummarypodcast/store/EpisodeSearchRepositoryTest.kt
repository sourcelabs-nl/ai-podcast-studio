package com.aisummarypodcast.store

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import java.time.Instant
import java.time.temporal.ChronoUnit

@SpringBootTest
class EpisodeSearchRepositoryTest {

    @Autowired lateinit var episodeSearchRepository: EpisodeSearchRepository
    @Autowired lateinit var episodeRepository: EpisodeRepository
    @Autowired lateinit var episodeArticleRepository: EpisodeArticleRepository
    @Autowired lateinit var articleRepository: ArticleRepository
    @Autowired lateinit var sourceRepository: SourceRepository
    @Autowired lateinit var podcastRepository: PodcastRepository
    @Autowired lateinit var userRepository: UserRepository

    private val page = PageRequest.of(0, 20)

    @BeforeEach
    fun setUp() {
        episodeArticleRepository.deleteAll()
        articleRepository.deleteAll()
        sourceRepository.deleteAll()
        episodeRepository.deleteAll()
        podcastRepository.deleteAll()
        userRepository.deleteAll()

        userRepository.save(User(id = "u1", name = "U"))
        podcastRepository.save(Podcast(id = "p1", userId = "u1", name = "P", topic = "tech"))
        sourceRepository.save(Source(id = "s1", podcastId = "p1", type = SourceType.RSS, url = "https://example.com/feed"))
    }

    private var ordinal = 0

    private fun episode(
        script: String = "nothing notable",
        status: EpisodeStatus = EpisodeStatus.GENERATED,
        recap: String? = null,
        showNotes: String? = null
    ): Long {
        ordinal++
        return episodeRepository.save(
            Episode(
                podcastId = "p1",
                generatedAt = Instant.now().minus(ordinal.toLong(), ChronoUnit.MINUTES).toString(),
                scriptText = script,
                status = status,
                recap = recap,
                showNotes = showNotes
            )
        ).id!!
    }

    /** Links an article to an episode. A null [topicOrder] means it was gathered but never covered. */
    private fun article(episodeId: Long, title: String, topic: String?, topicOrder: Int? = 1) {
        val saved = articleRepository.save(
            Article(
                sourceId = "s1",
                title = title,
                body = "body",
                url = "https://example.com/${System.nanoTime()}",
                contentHash = "h-${System.nanoTime()}"
            )
        )
        episodeArticleRepository.save(
            EpisodeArticle(episodeId = episodeId, articleId = saved.id!!, topic = topic, topicOrder = topicOrder)
        )
    }

    private fun search(vararg terms: String, statuses: Collection<EpisodeStatus> = emptyList()) =
        episodeSearchRepository.searchEpisodeIds("p1", statuses, terms.toList(), page)

    @Test
    fun `matches on a covered topic`() {
        val id = episode()
        article(id, title = "Unrelated headline", topic = "Recall trap in retriever configuration")

        assertEquals(listOf(id), search("retriever").episodeIds)
    }

    @Test
    fun `matches on a covered article title`() {
        val id = episode()
        article(id, title = "RAG is not dead, it just moved", topic = "Something else")

        assertEquals(listOf(id), search("rag").episodeIds)
    }

    @Test
    fun `matches on script text alone`() {
        val id = episode(script = "today we talk about vector databases")

        assertEquals(listOf(id), search("vector").episodeIds)
    }

    @Test
    fun `matches on recap and show notes`() {
        val recapId = episode(recap = "a recap mentioning kubernetes")
        val notesId = episode(showNotes = "show notes mentioning kubernetes")

        assertEquals(setOf(recapId, notesId), search("kubernetes").episodeIds.toSet())
    }

    @Test
    fun `matching is case-insensitive`() {
        val id = episode()
        article(id, title = "Qwen ships a small model", topic = null)

        assertEquals(listOf(id), search("QWEN").episodeIds)
    }

    @Test
    fun `an article that was never covered does not match`() {
        val id = episode()
        article(id, title = "Gathered but never composed", topic = "Dropped topic", topicOrder = null)

        assertTrue(search("composed").episodeIds.isEmpty())
        assertTrue(search("dropped").episodeIds.isEmpty())
    }

    @Test
    fun `every term must match`() {
        val id = episode(script = "we discussed retrieval at length")
        article(id, title = "Retrieval quality", topic = "Retrieval")

        assertEquals(listOf(id), search("retrieval").episodeIds)
        assertTrue(search("retrieval", "augmented").episodeIds.isEmpty())
    }

    @Test
    fun `terms may match different fields`() {
        val id = episode(script = "and then we ran the benchmark")
        article(id, title = "Qwen release notes", topic = null)

        assertEquals(listOf(id), search("qwen", "benchmark").episodeIds)
    }

    @Test
    fun `wildcard characters are matched literally`() {
        val plain = episode(script = "no special characters here")
        val literal = episode(script = "a 50% improvement")

        assertTrue(search("%").episodeIds.contains(literal))
        assertFalse(search("%").episodeIds.contains(plain))
        assertTrue(search("_").episodeIds.isEmpty())
    }

    @Test
    fun `only the headline portion of a long title matches`() {
        val id = episode()
        // Sources such as X store a whole post as the title; a term buried past the headline
        // window should not make the episode match, since the row could not show why it did.
        article(id, title = "A headline about agents. " + "filler ".repeat(200) + "buriedterm", topic = null)

        assertEquals(listOf(id), search("headline").episodeIds)
        assertTrue(search("buriedterm").episodeIds.isEmpty())
    }

    @Test
    fun `search respects the status filter`() {
        val generated = episode(script = "shared keyword")
        episode(script = "shared keyword", status = EpisodeStatus.DISCARDED)

        val result = search("keyword", statuses = listOf(EpisodeStatus.GENERATED))
        assertEquals(listOf(generated), result.episodeIds)
        assertEquals(1, result.total)
    }

    @Test
    fun `results are paged newest first with a total across pages`() {
        // Each fixture episode is generated a minute earlier than the one before, so creation
        // order is already newest-first and is exactly the order the search must return.
        val newestFirst = (1..5).map { episode(script = "paged keyword") }

        val firstPage = episodeSearchRepository.searchEpisodeIds("p1", emptyList(), listOf("paged"), PageRequest.of(0, 2))
        assertEquals(newestFirst.take(2), firstPage.episodeIds)
        assertEquals(5, firstPage.total)

        val secondPage = episodeSearchRepository.searchEpisodeIds("p1", emptyList(), listOf("paged"), PageRequest.of(1, 2))
        assertEquals(newestFirst.drop(2).take(2), secondPage.episodeIds)
        assertEquals(5, secondPage.total)
    }

    @Test
    fun `episodes of another podcast are excluded`() {
        podcastRepository.save(Podcast(id = "p2", userId = "u1", name = "P2", topic = "tech"))
        episodeRepository.save(
            Episode(podcastId = "p2", generatedAt = Instant.now().toString(), scriptText = "other podcast keyword")
        )

        assertTrue(search("keyword").episodeIds.isEmpty())
    }

    @Test
    fun `match details name the matching topics and titles`() {
        val id = episode()
        article(id, title = "Qwen release notes", topic = "Qwen 3.8 model release", topicOrder = 1)
        article(id, title = "Unrelated headline", topic = "Unrelated topic", topicOrder = 2)

        val details = episodeSearchRepository.findMatchDetails(listOf(id), listOf("qwen"), 5).getValue(id)

        assertEquals(listOf("Qwen 3.8 model release"), details.topics)
        assertEquals(listOf("Qwen release notes"), details.articleTitles)
    }

    @Test
    fun `match details are empty for a script-only hit`() {
        val id = episode(script = "we talked about observability")
        article(id, title = "Unrelated headline", topic = "Unrelated topic")

        assertTrue(episodeSearchRepository.findMatchDetails(listOf(id), listOf("observability"), 5).isEmpty())
    }

    @Test
    fun `match details are capped per episode`() {
        val id = episode()
        repeat(4) { index -> article(id, title = "Capped headline $index", topic = "Capped topic $index", topicOrder = index) }

        val details = episodeSearchRepository.findMatchDetails(listOf(id), listOf("capped"), 2).getValue(id)

        assertEquals(2, details.topics.size)
        assertEquals(2, details.articleTitles.size)
    }
}
