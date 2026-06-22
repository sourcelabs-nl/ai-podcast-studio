package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.store.Article
import com.aisummarypodcast.store.ArticleRepository
import com.aisummarypodcast.store.EpisodeArticleRepository
import com.aisummarypodcast.store.EpisodeRepository
import com.aisummarypodcast.store.Podcast
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class ArticleEligibilityService(
    private val articleRepository: ArticleRepository,
    private val episodeRepository: EpisodeRepository,
    private val episodeArticleRepository: EpisodeArticleRepository,
    private val appProperties: AppProperties
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun findEligibleArticles(sourceIds: List<String>, podcast: Podcast): List<Article> {
        val threshold = podcast.relevanceThreshold
        val candidates = articleRepository.findRelevantUnprocessedBySourceIds(sourceIds, threshold)
        val ageGateCutoff = resolveAgeGateCutoff(podcast)

        if (ageGateCutoff == null) {
            log.info("[Eligibility] No published episodes for podcast '{}' — no age gate applied, {} candidates", podcast.name, candidates.size)
            return candidates
        }

        val filtered = candidates.filter { article ->
            val articleDate = article.publishedAt ?: return@filter true
            articleDate >= ageGateCutoff
        }

        if (filtered.size < candidates.size) {
            log.info("[Eligibility] Age gate filtered {} → {} articles for podcast '{}' (cutoff: {})",
                candidates.size, filtered.size, podcast.name, ageGateCutoff)
        }

        return filtered
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

    private fun resolveAgeGateCutoff(podcast: Podcast): String? {
        val latestPublished = episodeRepository.findLatestPublishedByPodcastId(podcast.id)
        return latestPublished?.generatedAt
    }
}
