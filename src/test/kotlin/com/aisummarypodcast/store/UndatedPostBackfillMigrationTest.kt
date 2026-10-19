package com.aisummarypodcast.store

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Covers V88: an undated post or article takes the moment it was first seen. The migration has
 * already run on the test database, so its statements are re-run against rows written undated.
 */
@SpringBootTest
class UndatedPostBackfillMigrationTest {

    @Autowired lateinit var jdbcTemplate: JdbcTemplate
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var podcastRepository: PodcastRepository
    @Autowired lateinit var sourceRepository: SourceRepository
    @Autowired lateinit var postRepository: PostRepository
    @Autowired lateinit var articleRepository: ArticleRepository
    @Autowired lateinit var postArticleRepository: PostArticleRepository

    @BeforeEach
    fun setUp() {
        postArticleRepository.deleteAll()
        postRepository.deleteAll()
        articleRepository.deleteAll()
        sourceRepository.deleteAll()
        podcastRepository.deleteAll()
        userRepository.deleteAll()
        userRepository.save(User(id = "u1", name = "U"))
        podcastRepository.save(Podcast(id = "p1", userId = "u1", name = "P", topic = "tech"))
        sourceRepository.save(Source(id = "s1", podcastId = "p1", type = SourceType.RSS, url = "https://example.com/feed"))
    }

    private fun runBackfill() {
        val sql = ClassPathResource("db/migration/V88__date_undated_posts_and_index_post_url.sql")
            .inputStream.bufferedReader().readText()
        sql.lines().filterNot { it.trimStart().startsWith("--") }.joinToString("\n")
            .split(";").map { it.trim() }.filter { it.isNotEmpty() }
            .forEach { jdbcTemplate.execute(it) }
    }

    private fun post(url: String, createdAt: String, publishedAt: String? = null) = postRepository.save(
        Post(sourceId = "s1", title = "T", body = url, url = url, publishedAt = publishedAt, contentHash = url, createdAt = createdAt)
    )

    private fun article(hash: String, publishedAt: String? = null) = articleRepository.save(
        Article(sourceId = "s1", title = "A", body = "b", url = "https://example.com/$hash", publishedAt = publishedAt, contentHash = hash)
    )

    @Test
    fun `undated posts and articles take the first-seen moment, dated ones keep theirs`() {
        val undatedPost = post("https://example.com/1", createdAt = "2026-09-24T19:55:00Z")
        val laterPost = post("https://example.com/2", createdAt = "2026-10-08T19:42:00Z")
        val datedPost = post("https://example.com/3", createdAt = "2026-10-10T00:00:00Z", publishedAt = "2026-10-09T08:00:00Z")
        val undatedArticle = article("h1")
        postArticleRepository.save(PostArticle(postId = undatedPost.id!!, articleId = undatedArticle.id!!))
        postArticleRepository.save(PostArticle(postId = laterPost.id!!, articleId = undatedArticle.id))
        val datedArticle = article("h2", publishedAt = "2026-10-09T08:00:00Z")
        postArticleRepository.save(PostArticle(postId = datedPost.id!!, articleId = datedArticle.id!!))
        val orphanArticle = article("h3")

        runBackfill()

        assertEquals("2026-09-24T19:55:00Z", postRepository.findById(undatedPost.id).get().publishedAt)
        assertEquals("2026-10-09T08:00:00Z", postRepository.findById(datedPost.id).get().publishedAt)
        assertEquals("2026-09-24T19:55:00Z", articleRepository.findById(undatedArticle.id).get().publishedAt)
        assertEquals("2026-10-09T08:00:00Z", articleRepository.findById(datedArticle.id).get().publishedAt)
        assertNull(articleRepository.findById(orphanArticle.id!!).get().publishedAt)
    }
}
