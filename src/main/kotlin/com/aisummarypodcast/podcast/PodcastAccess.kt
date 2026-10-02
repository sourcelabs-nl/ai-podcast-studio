package com.aisummarypodcast.podcast

import com.aisummarypodcast.api.ResourceNotFoundException
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.user.UserService
import org.springframework.stereotype.Component

/**
 * The single definition of who may reach a podcast and its episodes: the user must exist, the
 * podcast must belong to the user, and the episode must belong to the podcast. Any miss throws
 * [ResourceNotFoundException], so a caller cannot tell a missing resource from someone else's.
 */
@Component
class PodcastAccess(
    private val userService: UserService,
    private val podcastService: PodcastService,
    private val episodeService: EpisodeService
) {

    fun podcast(userId: String, podcastId: String): Podcast {
        userService.findById(userId) ?: throw ResourceNotFoundException()
        val podcast = podcastService.findById(podcastId) ?: throw ResourceNotFoundException()
        if (podcast.userId != userId) throw ResourceNotFoundException()
        return podcast
    }

    fun episode(userId: String, podcastId: String, episodeId: Long): Episode {
        podcast(userId, podcastId)
        val episode = episodeService.findById(episodeId) ?: throw ResourceNotFoundException()
        if (episode.podcastId != podcastId) throw ResourceNotFoundException()
        return episode
    }
}
