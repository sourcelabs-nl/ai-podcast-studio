package com.aisummarypodcast.source

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.store.PostRepository
import com.aisummarypodcast.store.Source
import com.aisummarypodcast.store.SourceRepository
import com.aisummarypodcast.store.SourceType
import com.aisummarypodcast.util.sha256
import org.slf4j.LoggerFactory
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.data.repository.findByIdOrNull
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

    /**
     * Polls [source] and records the outcome on the source row.
     *
     * Returns the classified failure, or null on success. Failures are handled here rather than
     * thrown, but the classification is still returned so callers (the host circuit breaker) can
     * distinguish a host that is structurally down from one that is merely slow, without having to
     * re-read the row.
     */
    fun poll(
        source: Source,
        userId: String? = null,
        maxArticleAgeDays: Int? = null,
        siblingSourceIds: List<String> = emptyList()
    ): PollFailure? {
        log.info("[Polling] Polling source: {} ({})", source.url, source.type)

        try {
            val effectiveMaxArticleAgeDays = maxArticleAgeDays ?: appProperties.source.maxArticleAgeDays
            val maxAgeCutoff = Instant.now().minus(effectiveMaxArticleAgeDays.toLong(), ChronoUnit.DAYS)
            val isFirstPoll = source.lastPolled == null
            val sourceCreatedAt = parseInstant(source.createdAt)

            var resolvedXUserId: String? = null
            val rawPosts = when (source.type) {
                SourceType.RSS -> rssFeedFetcher.fetch(source.url, source.id, rssFetchFloor(source, maxAgeCutoff, isFirstPoll, sourceCreatedAt), source.categoryFilter)
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
            val now = Instant.now().toString()

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

            saveSourceState(source) { current ->
                current.copy(
                    lastPolled = Instant.now().toString(),
                    lastSeenId = newLastSeenId,
                    consecutiveFailures = 0,
                    lastFailureType = null
                )
            }
            log.info("[Polling] Source {} polled: {} new posts saved", source.url, savedCount)
            return null
        } catch (e: Exception) {
            val failure = PollFailure.classify(e)
            val failureType = failure.label
            val newFailureCount = source.consecutiveFailures + 1

            log.error("[Polling] {} failure polling source {} (attempt {}): {}",
                failureType, source.url, newFailureCount, failure.message, e)

            saveSourceState(source) { current ->
                val failureCount = current.consecutiveFailures + 1
                val updated = current.copy(
                    lastPolled = Instant.now().toString(),
                    consecutiveFailures = failureCount,
                    lastFailureType = failureType
                )

                val effectiveMaxFailures = current.maxFailures ?: appProperties.source.maxFailures
                if (failure is PollFailure.Permanent && failureCount >= effectiveMaxFailures) {
                    val reason = "Auto-disabled after $failureCount consecutive ${failure.message} errors"
                    log.warn("[Polling] Disabling source {}: {}", source.url, reason)
                    updated.copy(enabled = false, disabledReason = reason)
                } else {
                    updated
                }
            }
            return failure
        }
    }

    /**
     * Applies [update] to [source] and persists it, re-reading the row and reapplying [update] once
     * if a concurrent poll of the same source won the optimistic-lock race.
     *
     * A scheduled poll round can overlap a manual one, and the loser's `sources` update then fails
     * with [OptimisticLockingFailureException]. Left unhandled on the success path that exception is
     * caught as a *poll* failure, which inflates `consecutiveFailures` and can auto-disable a source
     * that is perfectly healthy. [update] therefore reads from the row it is given rather than the
     * stale copy, so the retry counts up from the concurrent writer's value.
     */
    private fun saveSourceState(source: Source, update: (Source) -> Source) {
        try {
            sourceRepository.save(update(source))
        } catch (e: OptimisticLockingFailureException) {
            val current = sourceRepository.findByIdOrNull(source.id)
            if (current == null) {
                log.warn("[Polling] Source {} was deleted while polling — discarding poll state", source.url)
                return
            }
            log.debug("[Polling] Retrying state update for source {} after a concurrent poll: {}", source.url, e.message)
            sourceRepository.save(update(current))
        }
    }

    /**
     * The earliest publish time worth fetching from an RSS source: the latest of the source's
     * `lastSeenId`, the max-article-age cutoff, and (on a first poll) the source's creation time.
     *
     * These bounds are applied again in the save loop below, but they must also reach the fetcher.
     * [RssFeedFetcher] deep-fetches each surviving entry's full article text, so a bound applied
     * only afterwards means every historical entry is crawled and then discarded. Adding
     * `openai.com/news/rss.xml` (1153 entries) that way ran for over 12 minutes and never
     * completed, because the whole archive was deep-fetched to keep nothing.
     *
     * Returned as the `lastSeenId` argument rather than a new parameter because it is the same
     * concept the fetcher already applies there: entries at or before this instant are skipped.
     */
    private fun rssFetchFloor(
        source: Source,
        maxAgeCutoff: Instant,
        isFirstPoll: Boolean,
        sourceCreatedAt: Instant
    ): String = listOfNotNull(
        source.lastSeenId?.let { parseInstant(it) },
        maxAgeCutoff,
        sourceCreatedAt.takeIf { isFirstPoll }
    ).max().toString()

    private fun parseInstant(text: String): Instant =
        try {
            Instant.parse(text)
        } catch (e: DateTimeParseException) {
            LocalDateTime.parse(text.replace(' ', 'T')).toInstant(ZoneOffset.UTC)
        }

}
