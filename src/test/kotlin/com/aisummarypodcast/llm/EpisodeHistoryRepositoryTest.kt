package com.aisummarypodcast.llm

import com.aisummarypodcast.store.Article
import com.aisummarypodcast.store.ArticleRepository
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodeArticle
import com.aisummarypodcast.store.EpisodeArticleRepository
import com.aisummarypodcast.store.EpisodeRepository
import com.aisummarypodcast.store.EpisodeStatus
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.PodcastRepository
import com.aisummarypodcast.store.Source
import com.aisummarypodcast.store.SourceRepository
import com.aisummarypodcast.store.SourceType
import com.aisummarypodcast.store.User
import com.aisummarypodcast.store.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant

@SpringBootTest
class EpisodeHistoryRepositoryTest {

    @Autowired lateinit var episodeHistoryRepository: EpisodeHistoryRepository
    @Autowired lateinit var episodeRepository: EpisodeRepository
    @Autowired lateinit var episodeArticleRepository: EpisodeArticleRepository
    @Autowired lateinit var articleRepository: ArticleRepository
    @Autowired lateinit var sourceRepository: SourceRepository
    @Autowired lateinit var podcastRepository: PodcastRepository
    @Autowired lateinit var userRepository: UserRepository

    @BeforeEach
    fun setUp() {
        episodeArticleRepository.deleteAll()
        episodeRepository.deleteAll()
        articleRepository.deleteAll()
        sourceRepository.deleteAll()
        podcastRepository.deleteAll()
        userRepository.deleteAll()

        userRepository.save(User(id = "u1", name = "Test User"))
        podcastRepository.save(Podcast(id = "pA", userId = "u1", name = "A", topic = "tech"))
        podcastRepository.save(Podcast(id = "pB", userId = "u1", name = "B", topic = "tech"))
        sourceRepository.save(Source(id = "sA", podcastId = "pA", type = SourceType.RSS, url = "https://example.com/a"))
        sourceRepository.save(Source(id = "sB", podcastId = "pB", type = SourceType.RSS, url = "https://example.com/b"))
    }

    private fun seedEpisode(podcastId: String, sourceId: String, recap: String, scriptText: String, topic: String?): Long {
        val article = articleRepository.save(Article(
            sourceId = sourceId,
            title = "$topic-article",
            body = "body",
            url = "https://example.com/${topic}-${System.nanoTime()}",
            publishedAt = Instant.now().toString(),
            contentHash = "h-${System.nanoTime()}",
            isProcessed = true
        ))
        val episode = episodeRepository.save(Episode(
            podcastId = podcastId,
            generatedAt = Instant.now().toString(),
            scriptText = scriptText,
            status = EpisodeStatus.GENERATED,
            recap = recap
        ))
        episodeArticleRepository.save(EpisodeArticle(episodeId = episode.id!!, articleId = article.id!!, topic = topic))
        return episode.id
    }

    @Test
    fun `search returns matches scoped to the requested podcast`() {
        seedEpisode("pA", "sA", "We covered speckit and its rollout last week.", "intro about speckit and ai", "speckit")
        seedEpisode("pB", "sB", "We covered speckit on the other show.", "irrelevant", "speckit")

        val matches = episodeHistoryRepository.search("pA", "speckit")

        assertEquals(1, matches.size)
        assertTrue(matches[0].recapSnippet.contains("speckit", ignoreCase = true))
        assertEquals("speckit", matches[0].topics)
    }

    @Test
    fun `search is case-insensitive`() {
        seedEpisode("pA", "sA", "SpecKit announcement covered earlier.", "...", "speckit")

        val matches = episodeHistoryRepository.search("pA", "SPECKIT")

        assertEquals(1, matches.size)
    }

    @Test
    fun `search returns empty when nothing matches`() {
        seedEpisode("pA", "sA", "Talked about kubernetes.", "k8s", "kubernetes")

        val matches = episodeHistoryRepository.search("pA", "speckit")

        assertTrue(matches.isEmpty())
    }

    @Test
    fun `recap snippet is capped around 280 chars`() {
        val longRecap = "speckit ".repeat(200)
        seedEpisode("pA", "sA", longRecap, "...", "speckit")

        val matches = episodeHistoryRepository.search("pA", "speckit")

        assertEquals(1, matches.size)
        assertTrue(matches[0].recapSnippet.length <= 281, "snippet was ${matches[0].recapSnippet.length} chars")
    }

    @Test
    fun `topic-only match still surfaces an episode`() {
        seedEpisode("pA", "sA", "Generic recap with no keyword.", "no keyword in script", "speckit")

        val matches = episodeHistoryRepository.search("pA", "speckit")

        assertEquals(1, matches.size)
    }
}
