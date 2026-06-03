package com.aisummarypodcast.source

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.store.PostRepository
import com.aisummarypodcast.store.Source
import com.aisummarypodcast.store.SourceRepository
import com.aisummarypodcast.store.SourceType
import com.aisummarypodcast.util.sha256
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

@Component
class SourcePoller(
    private val rssFeedFetcher: RssFeedFetcher,
    private val websiteFetcher: WebsiteFetcher,
    private val twitterFetcher: TwitterFetcher,
    private val postRepository: PostRepository,
    private val sourceRepository: SourceRepository,
    private val appProperties: AppProperties
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun poll(source: Source, userId: String? = null, maxArticleAgeDays: Int? = null, siblingSourceIds: List<String> = emptyList()) {
        log.info("[Polling] Polling source: {} ({})", source.url, source.type)

        try {
            var resolvedXUserId: String? = null
            val rawPosts = when (source.type) {
                SourceType.RSS -> rssFeedFetcher.fetch(source.url, source.id, source.lastSeenId, source.categoryFilter)
                SourceType.WEBSITE -> listOfNotNull(websiteFetcher.fetch(source.url, source.id))
                SourceType.TWITTER -> {
                    if (userId == null) {
                        log.warn("[Polling] Twitter source {} requires user context for OAuth — skipping", source.url)
                        emptyList()
                    } else {
                        val result = twitterFetcher.fetchWithUserId(source.url, source.id, source.lastSeenId, userId)
                        resolvedXUserId = result.resolvedXUserId
                        result.posts
                    }
                }
                SourceType.YOUTUBE -> rssFeedFetcher.fetch(source.url, source.id, source.lastSeenId, source.categoryFilter, deepFetch = false)
            }

            var latestTimestamp = source.lastSeenId
            var savedCount = 0

            val effectiveMaxArticleAgeDays = maxArticleAgeDays ?: appProperties.source.maxArticleAgeDays
            val maxAgeCutoff = Instant.now().minus(effectiveMaxArticleAgeDays.toLong(), ChronoUnit.DAYS)
            val now = Instant.now().toString()

            val isFirstPoll = source.lastPolled == null
            val sourceCreatedAt = parseInstant(source.createdAt)

            for (post in rawPosts) {
                if (post.publishedAt != null && parseInstant(post.publishedAt).isBefore(maxAgeCutoff)) {
                    log.debug("[Polling] Skipping old post '{}' (published {})", post.title, post.publishedAt)
                    continue
                }

                if (isFirstPoll && post.publishedAt != null && parseInstant(post.publishedAt).isBefore(sourceCreatedAt)) {
                    log.debug("[Polling] Skipping pre-creation post '{}' (published {}, source created {})", post.title, post.publishedAt, source.createdAt)
                    continue
                }

                val hash = sha256(post.body)
                if (postRepository.findBySourceIdAndContentHash(source.id, hash) != null) continue

                if (siblingSourceIds.isNotEmpty() && postRepository.findByContentHashAndSourceIdIn(hash, siblingSourceIds) != null) {
                    log.debug("[Polling] Skipping cross-source duplicate '{}' (hash {})", post.title, hash)
                    continue
                }

                postRepository.save(post.copy(contentHash = hash, createdAt = now))
                savedCount++

                post.publishedAt?.let { publishedAt ->
                    if (latestTimestamp == null || publishedAt > latestTimestamp!!) {
                        latestTimestamp = publishedAt
                    }
                }
            }

            val newLastSeenId = if (source.type == SourceType.TWITTER && userId != null) {
                twitterFetcher.buildLastSeenId(source.lastSeenId, rawPosts, source.url, userId, resolvedXUserId)
            } else {
                latestTimestamp
            }

            sourceRepository.save(source.copy(
                lastPolled = Instant.now().toString(),
                lastSeenId = newLastSeenId,
                consecutiveFailures = 0,
                lastFailureType = null
            ))
            log.info("[Polling] Source {} polled: {} new posts saved", source.url, savedCount)
        } catch (e: Exception) {
            val failure = PollFailure.classify(e)
            val failureType = if (failure is PollFailure.Permanent) "permanent" else "transient"
            val newFailureCount = source.consecutiveFailures + 1

            log.error("[Polling] {} failure polling source {} (attempt {}): {}",
                failureType, source.url, newFailureCount, failure.message, e)

            var updatedSource = source.copy(
                lastPolled = Instant.now().toString(),
                consecutiveFailures = newFailureCount,
                lastFailureType = failureType
            )

            val effectiveMaxFailures = source.maxFailures ?: appProperties.source.maxFailures
            if (failure is PollFailure.Permanent && newFailureCount >= effectiveMaxFailures) {
                val reason = "Auto-disabled after $newFailureCount consecutive ${failure.message} errors"
                log.warn("[Polling] Disabling source {}: {}", source.url, reason)
                updatedSource = updatedSource.copy(enabled = false, disabledReason = reason)
            }

            sourceRepository.save(updatedSource)
        }
    }

    private fun parseInstant(text: String): Instant =
        try {
            Instant.parse(text)
        } catch (e: DateTimeParseException) {
            LocalDateTime.parse(text.replace(' ', 'T')).toInstant(ZoneOffset.UTC)
        }

}
