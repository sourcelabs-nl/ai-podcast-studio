package com.aisummarypodcast.source

import com.aisummarypodcast.store.Article
import com.aisummarypodcast.store.ArticleRepository
import com.aisummarypodcast.store.Post
import com.aisummarypodcast.store.PostArticle
import com.aisummarypodcast.store.PostArticleRepository
import com.aisummarypodcast.store.Source
import com.aisummarypodcast.store.SourceType
import com.aisummarypodcast.util.sha256
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.net.URI

/**
 * Title prefix marking a reply, and so the convention thread detection keys on. Nitter's RSS used
 * it natively; [RssFeedFetcher] rewrites other feeds' reply markers into the same shape.
 */
internal const val REPLY_TITLE_PREFIX = "R to @"

@Component
class SourceAggregator(
    private val articleRepository: ArticleRepository,
    private val postArticleRepository: PostArticleRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        // Narro prefixes each item's title with the posting account, e.g. "@ivanfioravanti: ...".
        private val TITLE_HANDLE_PREFIX = Regex("""^@([A-Za-z0-9_]{1,15})\s*:""")
    }

    @Transactional
    fun aggregateAndPersist(posts: List<Post>, source: Source): List<Article> {
        if (posts.isEmpty()) return emptyList()

        val articles = if (shouldAggregate(source) && posts.size > 1) {
            aggregatePosts(posts, source)
        } else {
            mapIndividualPosts(posts, source)
        }

        val savedArticles = articles.map { (article, threadPosts) ->
            val existing = articleRepository.findBySourceIdAndContentHash(source.id, article.contentHash)
            val saved = existing ?: articleRepository.save(article)
            saved to threadPosts
        }

        for ((article, threadPosts) in savedArticles) {
            for (post in threadPosts) {
                postArticleRepository.save(PostArticle(postId = post.id!!, articleId = article.id!!))
            }
        }

        val result = savedArticles.map { it.first }
        log.info("[Aggregator] Created {} articles from {} posts for source {}", result.size, posts.size, source.id)
        return result
    }

    internal fun shouldAggregate(source: Source): Boolean {
        if (source.aggregate != null) return source.aggregate
        return source.type == SourceType.TWITTER || source.url.contains("nitter.net")
    }

    internal fun groupPostsByThread(posts: List<Post>): List<List<Post>> {
        val sorted = posts.sortedBy { it.publishedAt ?: "" }
        val threads = mutableListOf<MutableList<Post>>()

        for (post in sorted) {
            if (isReply(post)) {
                if (threads.isNotEmpty()) {
                    threads.last().add(post)
                } else {
                    // Orphan reply: start a new thread
                    threads.add(mutableListOf(post))
                }
            } else {
                threads.add(mutableListOf(post))
            }
        }

        return threads
    }

    private fun isReply(post: Post): Boolean = post.title.startsWith(REPLY_TITLE_PREFIX)

    /**
     * Resolves the account a post belongs to.
     *
     * A source is no longer the same thing as an account: a combined feed (Narro) merges many X
     * accounts into one RSS document, so threading has to be scoped per author or the reply-
     * attachment rule in [groupPostsByThread] splices different people's posts together.
     *
     * The post URL is the most reliable of the three signals, because a combined feed rewrites
     * every item's link to the original post, so the handle survives even where the feed's own
     * author field carries a display name ("Ivan Fioravanti") or nothing at all.
     */
    internal fun resolveAuthorKey(post: Post): String? {
        xHandleFromUrl(post.url)?.let { return it }
        TITLE_HANDLE_PREFIX.find(post.title)?.groupValues?.get(1)?.lowercase()?.let { return it }
        return post.author?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
    }

    private fun xHandleFromUrl(url: String): String? {
        val uri = try {
            URI(url)
        } catch (_: Exception) {
            return null
        }
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
        if (host != "x.com" && host != "twitter.com") return null
        val segments = uri.path?.split('/')?.filter { it.isNotEmpty() } ?: return null
        if (segments.size < 3 || segments[1] != "status") return null
        return segments[0].lowercase()
    }

    private fun aggregatePosts(posts: List<Post>, source: Source): List<Pair<Article, List<Post>>> {
        // Posts whose author cannot be resolved share the null group, which for a feed carrying no
        // author information at all reproduces the single-group behaviour this replaced.
        val byAuthor = posts.groupBy { resolveAuthorKey(it) }
        val threads = byAuthor.values.flatMap { groupPostsByThread(it) }
        log.info("[Aggregator] Grouped {} posts into {} threads across {} author(s) for source {}",
            posts.size, threads.size, byAuthor.size, source.id)

        return threads.map { threadPosts ->
            val parent = threadPosts.first()
            val body = threadPosts.joinToString("\n\n---\n\n") { post ->
                val timestamp = post.publishedAt ?: ""
                if (timestamp.isNotEmpty()) "$timestamp\n${post.body}" else post.body
            }

            val article = Article(
                sourceId = source.id,
                title = parent.title,
                body = body,
                url = rewriteNitterUrl(parent.url),
                publishedAt = parent.publishedAt,
                author = parent.author,
                contentHash = sha256(body)
            )

            article to threadPosts
        }
    }

    private fun mapIndividualPosts(posts: List<Post>, source: Source): List<Pair<Article, List<Post>>> {
        return posts.map { post ->
            val article = Article(
                sourceId = source.id,
                title = post.title,
                body = post.body,
                url = post.url,
                publishedAt = post.publishedAt,
                author = post.author,
                contentHash = sha256(post.body)
            )
            article to listOf(post)
        }
    }

    internal fun rewriteNitterUrl(url: String): String {
        return try {
            val uri = URI(url)
            if (uri.host?.equals("nitter.net", ignoreCase = true) == true) {
                URI(uri.scheme, uri.userInfo, "x.com", uri.port, uri.path, uri.query, uri.fragment).toString()
            } else {
                url
            }
        } catch (_: Exception) {
            url
        }
    }
}
