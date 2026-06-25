package com.aisummarypodcast.publishing

import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.Podcast

data class PublishResult(
    val externalId: String,
    val externalUrl: String
)

interface EpisodePublisher {

    fun targetName(): String

    suspend fun publish(episode: Episode, podcast: Podcast, userId: String): PublishResult

    fun update(episode: Episode, podcast: Podcast, userId: String, externalId: String): PublishResult {
        throw UnsupportedOperationException("${targetName()} does not support updating published episodes")
    }

    fun unpublish(userId: String, externalId: String) {
        throw UnsupportedOperationException("${targetName()} does not support unpublishing")
    }

    /**
     * Called after the publication record is saved with PUBLISHED status.
     * Use this for actions that depend on the publication being recorded (e.g., uploading feed.xml).
     */
    fun postPublish(podcast: Podcast, userId: String) {
        // no-op by default
    }
}
