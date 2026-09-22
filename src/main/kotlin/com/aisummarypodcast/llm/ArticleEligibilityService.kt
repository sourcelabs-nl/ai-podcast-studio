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

        return filterToWindow(candidates, podcast, window)
    }

    /**
     * The candidate articles for a focus episode: not yet used and published inside the run's
     * window, whatever their relevance to the podcast's own topic. A focus can be about something
     * the podcast's topic scores low, so starting from the topic-relevant set would silently lose
     * exactly the articles the focus is after; the focus scoring pass judges relevance instead.
     */
    fun findEligibleArticlesForFocus(sourceIds: List<String>, podcast: Podcast, window: EpisodeWindow): List<Article> {
        val candidates = articleRepository.findUnprocessedSince(sourceIds, window.startIso)
        return filterToWindow(candidates, podcast, window)
    }

    private fun filterToWindow(candidates: List<Article>, podcast: Podcast, window: EpisodeWindow): List<Article> {
        val datable = dropEvergreen(candidates)

        val filtered = datable.filter { article ->
            val publishedAt = article.publishedAt?.let { parseInstantOrNull(it) } ?: return@filter true
            !publishedAt.isBefore(window.start) && publishedAt.isBefore(window.end)
        }

        if (filtered.size < datable.size) {
            log.info("[Eligibility] Window {} filtered {} → {} articles for podcast '{}'",
                window, datable.size, filtered.size, podcast.name)
        } else {
            log.info("[Eligibility] Window {} kept all {} candidate articles for podcast '{}'",
                window, datable.size, podcast.name)
        }

        return filtered
    }

    /**
     * Drops the [NewsType.EVERGREEN] articles, which carry no datable event and so have no place in
     * an episode about what happened.
     *
     * The window filter below cannot do this. An evergreen page's `publishedAt` is the moment
     * someone submitted the link, not the moment anything happened, so it lands inside every window
     * it is offered to and looks as fresh as a genuine announcement. Episode 222 read out the
     * openspec.dev landing page — star count, install line and compatibility list — because nothing
     * between fetching that page and composing the script could tell it apart from news.
     *
     * A null classification is kept, on the same grounds as an article with no `publishedAt`: a
     * classification we failed to obtain must not silently delete content. That also leaves every
     * article scored before [NewsType] existed untouched.
     */
    private fun dropEvergreen(candidates: List<Article>): List<Article> {
        val (evergreen, rest) = candidates.partition { NewsType.parse(it.newsType) == NewsType.EVERGREEN }
        if (evergreen.isEmpty()) return rest

        log.info("[Eligibility] Dropped {} evergreen article(s) with no datable development: {}",
            evergreen.size, evergreen.joinToString("; ") { "'${it.title}' (${it.url})" })
        return rest
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

    /**
     * What the podcast's recent episodes already covered, in both the forms the dedup stage needs.
     *
     * One pass over the same episode links yields both: the article rows they point at, and the
     * dedup cluster labels stored alongside them.
     */
    fun findHistory(podcast: Podcast): EpisodeHistory {
        val lookback = podcast.recapLookbackEpisodes ?: appProperties.episode.recapLookbackEpisodes
        val recentEpisodes = episodeRepository.findRecentGeneratedByPodcastId(podcast.id, lookback)
        if (recentEpisodes.isEmpty()) return EpisodeHistory.EMPTY

        val allArticles = mutableListOf<Article>()
        val topics = mutableListOf<String>()
        for (episode in recentEpisodes) {
            val links = episodeArticleRepository.findByEpisodeId(episode.id!!)
            for (link in links) {
                articleRepository.findByIdOrNull(link.articleId)?.let { allArticles.add(it) }
                link.topic?.takeIf { it.isNotBlank() }?.let { topics.add(it) }
            }
        }

        // Episodes arrive most-recent-first, so capping the deduped list keeps the freshest
        // articles and bounds the dedup prompt regardless of how many articles each episode pulled in.
        // Topics are not capped the same way: one label per cluster is an order of magnitude fewer
        // lines than the articles they came from, and a label dropped is a topic the filter forgets.
        return EpisodeHistory(
            articles = allArticles.distinctBy { it.id }.take(appProperties.llm.dedup.maxHistoricalArticles),
            coveredTopics = topics.distinct()
        )
    }
}
