package com.aisummarypodcast.store

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant

/**
 * The thread view on an article card needs two things from the store: how many posts an article was
 * aggregated from, and those posts in the order they were written.
 */
@SpringBootTest
class ArticlePostThreadTest {

    @Autowired lateinit var postRepository: PostRepository
    @Autowired lateinit var postArticleRepository: PostArticleRepository
    @Autowired lateinit var articleRepository: ArticleRepository
    @Autowired lateinit var episodeArticleRepository: EpisodeArticleRepository
    @Autowired lateinit var episodeRepository: EpisodeRepository
    @Autowired lateinit var sourceRepository: SourceRepository
    @Autowired lateinit var podcastRepository: PodcastRepository
    @Autowired lateinit var userRepository: UserRepository

    private var episodeId: Long = 0

    @BeforeEach
    fun setUp() {
        postArticleRepository.deleteAll()
        episodeArticleRepository.deleteAll()
        episodeRepository.deleteAll()
        postRepository.deleteAll()
        articleRepository.deleteAll()
        sourceRepository.deleteAll()
        podcastRepository.deleteAll()
        userRepository.deleteAll()

        userRepository.save(User(id = "u1", name = "Test User"))
        podcastRepository.save(Podcast(id = "p1", userId = "u1", name = "Test", topic = "tech"))
        sourceRepository.save(Source(id = "s1", podcastId = "p1", type = SourceType.RSS, url = "https://rss.narro.info/feed"))
        episodeId = episodeRepository.save(
            Episode(podcastId = "p1", generatedAt = Instant.now().toString(), scriptText = "script")
        ).id!!
    }

    private fun article(title: String, hash: String) = articleRepository.save(
        Article(sourceId = "s1", title = title, body = "body", url = "https://x.com/a/status/1", contentHash = hash)
    )

    private fun post(body: String, hash: String, publishedAt: String) = postRepository.save(
        Post(
            sourceId = "s1", title = body, body = body, url = "https://x.com/a/status/$hash",
            publishedAt = publishedAt, contentHash = hash, createdAt = Instant.now().toString()
        )
    )

    private fun link(articleId: Long, postId: Long) =
        postArticleRepository.save(PostArticle(postId = postId, articleId = articleId))

    @Test
    fun `episode article reports how many posts it was aggregated from`() {
        val thread = article("A 3-post thread", "h-thread")
        val single = article("A lone post", "h-single")
        repeat(3) { i -> link(thread.id!!, post("post $i", "p$i", "2026-08-31T1$i:00:00Z").id!!) }
        link(single.id!!, post("only", "p-only", "2026-08-31T10:00:00Z").id!!)
        episodeArticleRepository.save(EpisodeArticle(episodeId = episodeId, articleId = thread.id!!))
        episodeArticleRepository.save(EpisodeArticle(episodeId = episodeId, articleId = single.id!!))

        val articles = episodeArticleRepository.findArticlesWithSourcesByEpisodeId(episodeId)

        assertEquals(3, articles.first { it.title == "A 3-post thread" }.postCount)
        assertEquals(1, articles.first { it.title == "A lone post" }.postCount)
    }

    @Test
    fun `an article with no post links still counts as one`() {
        val plain = article("A plain RSS article", "h-plain")
        episodeArticleRepository.save(EpisodeArticle(episodeId = episodeId, articleId = plain.id!!))

        val articles = episodeArticleRepository.findArticlesWithSourcesByEpisodeId(episodeId)

        assertEquals(1, articles.single().postCount)
    }

    @Test
    fun `posts of an article are returned oldest first`() {
        val thread = article("Thread", "h-thread")
        // Linked out of order so the ordering under test cannot come from insertion order.
        link(thread.id!!, post("third", "p3", "2026-08-31T12:00:00Z").id!!)
        link(thread.id!!, post("first", "p1", "2026-08-31T10:00:00Z").id!!)
        link(thread.id!!, post("second", "p2", "2026-08-31T11:00:00Z").id!!)

        val posts = postRepository.findPostsByArticleId(thread.id!!)

        assertEquals(listOf("first", "second", "third"), posts.map { it.body })
    }

    @Test
    fun `post counts are returned keyed by article id`() {
        val a = article("A", "h-a")
        val b = article("B", "h-b")
        repeat(2) { i -> link(a.id!!, post("a$i", "pa$i", "2026-08-31T10:0$i:00Z").id!!) }
        link(b.id!!, post("b0", "pb0", "2026-08-31T10:00:00Z").id!!)

        val counts = postRepository.getPostCountsByArticleIds(listOf(a.id!!, b.id!!))

        assertEquals(2, counts[a.id!!])
        assertEquals(1, counts[b.id!!])
    }

    @Test
    fun `post counts for an article with no posts are absent, so the mapper can default to one`() {
        val plain = article("Plain", "h-plain")

        val counts = postRepository.getPostCountsByArticleIds(listOf(plain.id!!))

        assertTrue(counts.isEmpty())
    }

    @Test
    fun `no article ids returns no counts`() {
        assertTrue(postRepository.getPostCountsByArticleIds(emptyList()).isEmpty())
    }
}
