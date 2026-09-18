package com.aisummarypodcast.llm

import com.aisummarypodcast.store.Article

/**
 * What recent episodes already covered, as the dedup stage is shown it.
 *
 * Two views of the same episodes, because they recall different things. [articles] are the source
 * items those episodes drew on, and recognise a story by its headline. [coveredTopics] are the
 * cluster labels the dedup stage itself assigned on those runs, persisted on `episode_articles`,
 * and recognise a story by what the show decided it was about.
 *
 * The second is what episode 222 lacked. It opened a DeepSeek segment as a fresh release while the
 * previous episode already carried the label "DeepSeek v4.1 Flash vs GLM 5.3 Flash comparison" —
 * but the article behind that label was a tweet titled about the GLM head-to-head, with "DeepSeek"
 * nowhere in its title. Given titles alone there was nothing to match on; given the labels there
 * is.
 */
data class EpisodeHistory(
    val articles: List<Article>,
    val coveredTopics: List<String>
) {
    companion object {
        val EMPTY = EpisodeHistory(emptyList(), emptyList())
    }
}
