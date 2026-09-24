package com.aisummarypodcast.source

import com.aisummarypodcast.store.Post
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import java.time.Instant
import java.time.format.DateTimeFormatter

data class TwitterFetchResult(val posts: List<Post>, val resolvedXUserId: String?)

data class LastSeenCursor(val xUserId: String?, val sinceId: String?)

@Component
class TwitterFetcher(
    private val xTokenManager: XTokenManager,
    private val xClient: XClient
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun fetch(url: String, sourceId: String, lastSeenId: String?, userId: String): List<Post> {
        return fetchWithUserId(url, sourceId, lastSeenId, userId).posts
    }

    fun fetchWithUserId(url: String, sourceId: String, lastSeenId: String?, userId: String): TwitterFetchResult {
        return try {
            val username = extractUsername(url)
            val accessToken = xTokenManager.getValidAccessToken(userId)
            val (cachedUserId, sinceId) = parseLastSeenId(lastSeenId)

            val xUserId = cachedUserId ?: resolveUserId(accessToken, username)
            val tweets = xClient.getUserTimeline(accessToken, xUserId, sinceId)

            TwitterFetchResult(
                posts = tweets.map { tweet -> mapTweetToPost(tweet, username, sourceId) },
                resolvedXUserId = xUserId
            )
        } catch (e: HttpClientErrorException) {
            handleApiError(e, sourceId)
            TwitterFetchResult(emptyList(), null)
        } catch (e: Exception) {
            log.error("[Twitter] Error fetching source {}: {}", sourceId, e.message)
            TwitterFetchResult(emptyList(), null)
        }
    }

    fun buildLastSeenId(currentLastSeenId: String?, posts: List<Post>, url: String, userId: String, resolvedXUserId: String? = null): String? {
        val (cachedUserId, currentSinceId) = parseLastSeenId(currentLastSeenId)
        val xUserId = cachedUserId ?: resolvedXUserId

        if (xUserId == null) return currentLastSeenId

        if (posts.isEmpty()) {
            return "$xUserId:${currentSinceId ?: ""}"
        }

        val latestTweetId = posts.mapNotNull { post ->
            post.url.substringAfterLast("/").toLongOrNull()
        }.maxOrNull()

        return if (latestTweetId != null) {
            "$xUserId:$latestTweetId"
        } else {
            "$xUserId:${currentSinceId ?: ""}"
        }
    }

    internal fun extractUsername(url: String): String {
        val cleaned = url.trim().removeSuffix("/")
        // Handle full URLs like https://x.com/username or https://twitter.com/username
        val urlPattern = Regex("https?://(?:x\\.com|twitter\\.com)/([a-zA-Z0-9_]+)")
        val match = urlPattern.find(cleaned)
        if (match != null) {
            return match.groupValues[1]
        }
        // Handle @username or plain username
        return cleaned.removePrefix("@")
    }

    internal fun parseLastSeenId(lastSeenId: String?): LastSeenCursor {
        if (lastSeenId == null) return LastSeenCursor(null, null)
        val parts = lastSeenId.split(":", limit = 2)
        return if (parts.size == 2) {
            LastSeenCursor(parts[0], parts[1].ifEmpty { null })
        } else {
            LastSeenCursor(null, null)
        }
    }

    private fun resolveUserId(accessToken: String, username: String): String {
        return try {
            xClient.resolveUsername(accessToken, username).id
        } catch (e: HttpClientErrorException) {
            if (xClient.isNotFound(e)) {
                throw RuntimeException("X user not found: $username")
            }
            throw e
        }
    }

    internal fun mapTweetToPost(tweet: XTweet, username: String, sourceId: String): Post {
        val title = if (tweet.text.length > 100) {
            tweet.text.take(100) + "..."
        } else {
            tweet.text
        }

        val publishedAt = tweet.createdAt?.let {
            try {
                Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(it)).toString()
            } catch (_: Exception) {
                it
            }
        }

        return Post(
            sourceId = sourceId,
            title = title,
            body = tweet.text,
            url = "https://x.com/$username/status/${tweet.id}",
            publishedAt = publishedAt,
            author = "@$username",
            contentHash = "",
            createdAt = ""
        )
    }

    private fun handleApiError(e: HttpClientErrorException, sourceId: String) {
        when {
            xClient.isRateLimited(e) -> log.warn("[Twitter] Rate limited for source {}: {}", sourceId, e.message)
            xClient.isAuthError(e) -> log.error("[Twitter] Auth error for source {} — OAuth token may be invalid or revoked: {}", sourceId, e.message)
            xClient.isNotFound(e) -> log.error("[Twitter] User not found for source {}: {}", sourceId, e.message)
            else -> log.error("[Twitter] API error for source {}: {}", sourceId, e.message, e)
        }
    }
}
