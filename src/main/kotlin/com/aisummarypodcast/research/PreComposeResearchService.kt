package com.aisummarypodcast.research

import com.aisummarypodcast.llm.EpisodeHistoryRepository
import com.aisummarypodcast.llm.PastEpisodeMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlin.coroutines.cancellation.CancellationException

/** Web searches in flight at once for one episode. */
private const val SEARCH_CONCURRENCY = 3

/**
 * The research stage that runs before compose, so compose needs no tools and completes in a single
 * model call. A tool call used to force a second compose round that re-sent and re-reasoned over the
 * whole conversation, costing minutes for lookups that took a second.
 *
 * Past coverage is looked up for every episode. Web search runs only where research is enabled (a
 * focus episode, or a podcast with deep-dive research): a planning call turns the subjects into
 * queries, which are searched concurrently, and the results are recorded against the episode.
 * Nothing here fails the episode: a failed plan searches the subjects, a failed search contributes
 * nothing.
 */
@Service
class PreComposeResearchService(
    private val researchPlanner: ResearchPlanner,
    private val researchService: ResearchService,
    private val episodeHistoryRepository: EpisodeHistoryRepository,
    private val researchSourceRecorder: ResearchSourceRecorder
) {

    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun research(request: ResearchRequest): PreComposeResearch = withContext(Dispatchers.IO) {
        val history = lookUpHistory(request)
        if (!request.webSearchEnabled || request.subjects.isEmpty()) {
            return@withContext PreComposeResearch(history = history)
        }

        val queries = researchPlanner.plan(request)
        val sources = search(request, queries)
        request.episodeId?.let { researchSourceRecorder.replace(it, sources) }
        log.info(
            "[Research] Podcast '{}' ({}): {} queries, {} sources, {} past-episode matches",
            request.podcast.name, request.podcast.id, queries.size, sources.size, history.size
        )
        PreComposeResearch(sources = sources, history = history, researchCalls = queries.size)
    }

    private suspend fun search(request: ResearchRequest, queries: List<String>): List<BackgroundSource> {
        val permits = Semaphore(SEARCH_CONCURRENCY)
        return coroutineScope {
            queries.map { query ->
                async { permits.withPermit { searchOne(request, query) } }
            }.awaitAll().flatten()
        }
    }

    private fun searchOne(request: ResearchRequest, query: String): List<BackgroundSource> =
        try {
            researchService.search(request.podcast.userId, query, RESULTS_PER_QUERY).results.map {
                BackgroundSource(query = query, title = it.title, url = it.url, snippet = it.content)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("[Research] Web search failed for query '{}': {}", query, e.message)
            emptyList()
        }

    private fun lookUpHistory(request: ResearchRequest): List<PastEpisodeMatch> =
        request.subjects.take(HISTORY_SUBJECT_CAP)
            .flatMap { episodeHistoryRepository.search(request.podcast.id, it, HISTORY_MATCHES_PER_SUBJECT) }
            .distinctBy { it.episodeId }
}
