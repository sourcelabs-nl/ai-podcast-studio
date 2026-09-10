package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.podcast.EpisodeWindow
import com.aisummarypodcast.store.Article
import com.aisummarypodcast.store.ArticleRepository
import com.aisummarypodcast.store.EpisodeArticleRepository
import com.aisummarypodcast.store.EpisodeRepository
import com.aisummarypodcast.store.Podcast
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

@Service
class ArticleEligibilityService(
    private val articleRepository: ArticleRepository,
    private val episodeRepository: EpisodeRepository,
    private val episodeArticleRepository: EpisodeArticleRepository,
    private val appProperties: AppProperties
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * The candidate articles for a run: relevant, not yet used, and published inside the run's
     * window. The window is the run's own input rather than something derived here, so a retry or a
     * re-run of a past day selects from the same window the episode was created with.
     *
     * An article without a `publishedAt` cannot be placed in time and is always kept: dropping it
     * would silently lose content from sources that publish without a date.
     */
    fun findEligibleArticles(sourceIds: List<String>, podcast: Podcast, window: EpisodeWindow): List<Article> {
        val threshold = podcast.relevanceThreshold
        val candidates = articleRepository.findRelevantUnprocessedBySourceIds(sourceIds, threshold)

        val filtered = candidates.filter { article ->
            val publishedAt = article.publishedAt?.let { parseInstantOrNull(it) } ?: return@filter true
            !publishedAt.isBefore(window.start) && publishedAt.isBefore(window.end)
        }

        if (filtered.size < candidates.size) {
            log.info("[Eligibility] Window {} filtered {} → {} articles for podcast '{}'",
                window, candidates.size, filtered.size, podcast.name)
        } else {
            log.info("[Eligibility] Window {} kept all {} candidate articles for podcast '{}'",
                window, candidates.size, podcast.name)
        }

        return filtered
    }

    /**
     * Instants are compared as instants, not as strings: `published_at` is written by several
     * fetchers and an offset form or a differing precision would order wrongly under a lexicographic
     * comparison. An unparseable value returns null and the article is kept rather than dropped.
     */
    private fun parseInstantOrNull(value: String): Instant? {
        return try {
            Instant.parse(value)
        } catch (_: DateTimeParseException) {
            try {
                OffsetDateTime.parse(value).toInstant()
            } catch (_: DateTimeParseException) {
                log.warn("[Eligibility] Unparseable published_at '{}', keeping the article", value)
                null
            }
        }
    }

    fun canResetArticle(articleId: Long): Boolean {
        return !episodeArticleRepository.isArticleLinkedToPublishedEpisode(articleId)
    }

    fun findHistoricalArticles(podcast: Podcast): List<Article> {
        val lookback = podcast.recapLookbackEpisodes ?: appProperties.episode.recapLookbackEpisodes
        val recentEpisodes = episodeRepository.findRecentGeneratedByPodcastId(podcast.id, lookback)
        if (recentEpisodes.isEmpty()) return emptyList()

        val allArticles = mutableListOf<Article>()
        for (episode in recentEpisodes) {
            val links = episodeArticleRepository.findByEpisodeId(episode.id!!)
            for (link in links) {
                articleRepository.findByIdOrNull(link.articleId)?.let { allArticles.add(it) }
            }
        }

        // Episodes arrive most-recent-first, so capping the deduped list keeps the freshest
        // articles and bounds the dedup prompt regardless of how many articles each episode pulled in.
        return allArticles.distinctBy { it.id }.take(appProperties.llm.dedup.maxHistoricalArticles)
    }
}
