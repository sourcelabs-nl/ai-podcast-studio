package com.aisummarypodcast.podcast

import com.aisummarypodcast.store.EpisodeMatchDetails
import com.aisummarypodcast.store.EpisodeRepository
import com.aisummarypodcast.store.EpisodeSearchRepository
import com.aisummarypodcast.store.EpisodeStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

@Service
class EpisodeSearchService(
    private val episodeSearchRepository: EpisodeSearchRepository,
    private val episodeRepository: EpisodeRepository
) {

    /**
     * Episodes matching [query], newest first, each paired with what made it match.
     *
     * Only ids are searched in SQL; the entities come back through [EpisodeRepository] so the
     * episode row mapping stays in one place rather than being duplicated as a hand-written mapper
     * over forty-odd columns.
     */
    fun search(
        podcastId: String,
        statuses: Collection<EpisodeStatus>,
        query: String,
        pageable: Pageable
    ): Page<EpisodeSearchHit> {
        val terms = parseTerms(query)
        val page = episodeSearchRepository.searchEpisodeIds(podcastId, statuses, terms, pageable)
        if (page.episodeIds.isEmpty()) return PageImpl(emptyList(), pageable, page.total)

        val byId = episodeRepository.findAllById(page.episodeIds).associateBy { it.id }
        // One past the cap: the extra entry is how the response knows to say "and more".
        val details = episodeSearchRepository.findMatchDetails(page.episodeIds, terms, MAX_MATCHES_PER_EPISODE + 1)

        // Re-order by the id list: findAllById gives no ordering guarantee, and the sort is the query's.
        val hits = page.episodeIds.mapNotNull { id ->
            byId[id]?.let { episode ->
                EpisodeSearchHit(
                    episode = episode,
                    matches = details[id] ?: EMPTY_MATCH,
                    // Computed here rather than in SQL: the episode text is already loaded.
                    scriptContext = ScriptSnippet.firstMatch(
                        listOf(episode.scriptText, episode.recap, episode.showNotes),
                        terms
                    )
                )
            }
        }
        return PageImpl(hits, pageable, page.total)
    }

    companion object {
        /** Below this a query is noise (and matches nearly everything), so it is ignored. */
        const val MIN_QUERY_LENGTH = 2

        /** Bounds the match details in the response; the row indicates when more exist. */
        const val MAX_MATCHES_PER_EPISODE = 5

        private val EMPTY_MATCH = EpisodeMatchDetails(emptyList(), emptyList())

        /** True when [query] is worth searching for, i.e. it has a term of usable length. */
        fun isSearchable(query: String?): Boolean = parseTerms(query ?: "").isNotEmpty()

        /**
         * Splits a query into the terms an episode must all match. Terms shorter than
         * [MIN_QUERY_LENGTH] are dropped rather than failing the search, so a trailing initial
         * while typing does not blank the results.
         */
        fun parseTerms(query: String): List<String> =
            query.trim().split(Regex("\\s+"))
                .filter { it.length >= MIN_QUERY_LENGTH }
    }
}
