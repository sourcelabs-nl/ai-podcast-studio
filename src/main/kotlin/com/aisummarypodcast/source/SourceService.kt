package com.aisummarypodcast.source

import com.aisummarypodcast.store.ArticleRepository
import com.aisummarypodcast.store.PostRepository
import com.aisummarypodcast.store.Source
import com.aisummarypodcast.store.SourceRepository
import com.aisummarypodcast.store.SourceType
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class SourceService(
    private val sourceRepository: SourceRepository,
    private val articleRepository: ArticleRepository,
    private val postRepository: PostRepository,
    private val rssFeedFetcher: RssFeedFetcher,
    private val websiteFetcher: WebsiteFetcher
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Deletes unprocessed articles and unlinked posts older than [maxArticleAgeDays]. Both deletes run
     * in one transaction so the cleanup is atomic.
     */
    @Transactional
    fun cleanupOldArticlesAndPosts(maxArticleAgeDays: Int) {
        val cutoff = Instant.now().minus(maxArticleAgeDays.toLong(), ChronoUnit.DAYS).toString()
        articleRepository.deleteOldUnprocessedArticles(cutoff)
        postRepository.deleteOldUnlinkedPosts(cutoff)
    }

    fun create(podcastId: String, type: SourceType, url: String, config: SourceConfig = SourceConfig()): Source {
        validateUrl(type, url, config.categoryFilter)
        val source = Source(
            id = UUID.randomUUID().toString(),
            podcastId = podcastId,
            type = type,
            url = url,
            pollIntervalMinutes = config.pollIntervalMinutes,
            enabled = config.enabled,
            aggregate = config.aggregate,
            maxFailures = config.maxFailures,
            maxBackoffHours = config.maxBackoffHours,
            pollDelaySeconds = config.pollDelaySeconds,
            categoryFilter = config.categoryFilter,
            label = config.label,
            createdAt = Instant.now().toString()
        )
        return sourceRepository.save(source)
    }

    internal fun validateUrl(type: SourceType, url: String, categoryFilter: String? = null) {
        when (type) {
            SourceType.RSS -> validateRssUrl(url, categoryFilter)
            SourceType.WEBSITE -> validateWebsiteUrl(url)
            else -> {} // Twitter, Reddit, YouTube — skip validation
        }
    }

    private fun validateRssUrl(url: String, categoryFilter: String?) {
        try {
            val posts = rssFeedFetcher.fetch(url, "validation", null, categoryFilter)
            if (posts.isEmpty()) {
                throw IllegalArgumentException("RSS feed at $url returned no items")
            }
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            val message = when {
                e.message?.contains("Invalid XML", ignoreCase = true) == true ||
                    e.message?.contains("Content is not allowed in prolog", ignoreCase = true) == true ->
                    "URL does not appear to be a valid RSS/Atom feed"
                e.message?.contains("UnknownHost", ignoreCase = true) == true ||
                    e.message?.contains("Connection refused", ignoreCase = true) == true ||
                    e.message?.contains("connect timed out", ignoreCase = true) == true ->
                    "Could not reach URL: ${e.message}"
                else -> "RSS feed at $url returned no content: ${e.message}"
            }
            throw IllegalArgumentException(message)
        }
    }

    private fun validateWebsiteUrl(url: String) {
        try {
            val post = websiteFetcher.fetch(url, "validation")
            if (post == null || post.body.isBlank()) {
                throw IllegalArgumentException("Website at $url returned no extractable content")
            }
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("Could not fetch website at $url: ${e.message}")
        }
    }

    fun findByPodcastId(podcastId: String): List<Source> = sourceRepository.findByPodcastId(podcastId)

    fun findById(sourceId: String): Source? = sourceRepository.findByIdOrNull(sourceId)

    fun update(sourceId: String, type: SourceType, url: String, config: SourceConfig): Source? {
        val source = findById(sourceId) ?: return null
        val reEnabling = config.enabled && !source.enabled
        var updated = source.copy(type = type, url = url, pollIntervalMinutes = config.pollIntervalMinutes, enabled = config.enabled, aggregate = config.aggregate, maxFailures = config.maxFailures, maxBackoffHours = config.maxBackoffHours, pollDelaySeconds = config.pollDelaySeconds, categoryFilter = config.categoryFilter, label = config.label)
        if (reEnabling) {
            updated = updated.copy(consecutiveFailures = 0, lastFailureType = null, disabledReason = null)
        }
        return sourceRepository.save(updated)
    }

    fun getArticleCounts(sourceIds: List<String>, relevanceThreshold: Int): Map<String, SourceArticleCounts> {
        return articleRepository.getArticleCountsBySourceIds(sourceIds, relevanceThreshold)
    }

    fun getPostCounts(sourceIds: List<String>): Map<String, Int> {
        return postRepository.getPostCountsBySourceIds(sourceIds)
    }

    @Transactional
    fun delete(sourceId: String): Boolean {
        val source = findById(sourceId) ?: return false
        postRepository.deleteBySourceId(sourceId)
        articleRepository.deleteBySourceId(sourceId)
        sourceRepository.delete(source)
        log.info("Deleted source {} and its posts and articles", sourceId)
        return true
    }
}
