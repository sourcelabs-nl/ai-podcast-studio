package com.aisummarypodcast.store

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

/**
 * What became of an article that stood as a candidate for an episode.
 *
 * The three dropping outcomes are worth telling apart: a gate exclusion and a clustering duplicate
 * are different decisions taken by different calls, and a compose cut is not a judgement about the
 * article at all.
 */
enum class CandidateOutcome {
    /** Reached the script. */
    USED,

    /** The already-covered gate judged its topic already covered by a recent episode. */
    EXCLUDED_BY_GATE,

    /** The clustering call placed it in a cluster that selected other articles, or none. */
    DROPPED_AS_DUPLICATE,

    /** Survived dedup, but fell outside `app.compose.max-articles` by relevance. */
    CUT_BY_COMPOSE_CAP
}

/**
 * An article scored as a candidate for one episode, and what became of it.
 *
 * Deliberately not a column on [EpisodeArticle]. That table answers which articles an episode
 * covers, and the show notes, the sources file, the recap and the marking of articles as processed
 * all read it on those terms; a dropped candidate must reach none of them.
 */
@Table("episode_candidate_articles")
data class EpisodeCandidateArticle(
    @Id val id: Long? = null,
    val episodeId: Long,
    val articleId: Long,
    val outcome: CandidateOutcome
)
