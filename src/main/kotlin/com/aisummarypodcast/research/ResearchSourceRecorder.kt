package com.aisummarypodcast.research

import com.aisummarypodcast.store.EpisodeResearchSource
import com.aisummarypodcast.store.EpisodeResearchSourceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Records the web search results of an episode's research run. A run replaces what an earlier run
 * recorded, so a retry or a feedback recompose leaves exactly the sources the current script saw.
 */
@Service
class ResearchSourceRecorder(
    private val researchSourceRepository: EpisodeResearchSourceRepository
) {

    @Transactional
    fun replace(episodeId: Long, sources: List<BackgroundSource>) {
        researchSourceRepository.deleteByEpisodeId(episodeId)
        sources.forEachIndexed { index, source ->
            researchSourceRepository.save(
                EpisodeResearchSource(
                    episodeId = episodeId,
                    query = source.query,
                    title = source.title,
                    url = source.url,
                    ordinal = index
                )
            )
        }
    }
}
