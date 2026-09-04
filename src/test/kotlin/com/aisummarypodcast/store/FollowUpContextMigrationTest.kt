package com.aisummarypodcast.store

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * Covers V66: `episode_articles.follow_up_context`. A link written without it (the shape of every
 * row that existed before the migration, including the episodes generated today) must read back as
 * null rather than failing, because a regeneration of such an episode has to fall back to
 * recomposing with no annotations.
 */
@SpringBootTest
class FollowUpContextMigrationTest {

    @Autowired lateinit var episodeArticleRepository: EpisodeArticleRepository
    @Autowired lateinit var articleRepository: ArticleRepository
    @Autowired lateinit var sourceRepository: SourceRepository
    @Autowired lateinit var podcastRepository: PodcastRepository
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var episodeRepository: EpisodeRepository

    private var episodeId: Long = 0

    @BeforeEach
    fun setUp() {
        episodeArticleRepository.deleteAll()
        articleRepository.deleteAll()
        sourceRepository.deleteAll()
        episodeRepository.deleteAll()
        podcastRepository.deleteAll()
        userRepository.deleteAll()

        userRepository.save(User(id = "u1", name = "Test User"))
        podcastRepository.save(Podcast(id = "p1", userId = "u1", name = "Test", topic = "tech"))
        sourceRepository.save(Source(id = "s1", podcastId = "p1", type = SourceType.RSS, url = "https://example.com/feed"))
        episodeId = episodeRepository.save(
            Episode(podcastId = "p1", generatedAt = "2026-09-04T13:00:00Z", scriptText = "")
        ).id!!
    }

    private fun saveArticle(hash: String): Long = articleRepository.save(
        Article(sourceId = "s1", title = "A", body = "body", url = "https://example.com/$hash", contentHash = hash)
    ).id!!

    @Test
    fun `follow-up context round-trips on an episode-article link`() {
        val articleId = saveArticle("h1")

        episodeArticleRepository.insertIgnore(episodeId, articleId, "Topic", 0, "Covered the launch two episodes ago")

        val link = episodeArticleRepository.findByEpisodeId(episodeId).single()
        assertEquals("Covered the launch two episodes ago", link.followUpContext)
    }

    @Test
    fun `a link written without a context reads back as null`() {
        val articleId = saveArticle("h2")

        episodeArticleRepository.insertIgnore(episodeId, articleId, "Topic", 0)

        val link = episodeArticleRepository.findByEpisodeId(episodeId).single()
        assertNull(link.followUpContext)
    }
}
