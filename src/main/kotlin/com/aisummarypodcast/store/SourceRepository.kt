package com.aisummarypodcast.store

import org.springframework.data.repository.CrudRepository

interface SourceRepository : CrudRepository<Source, String> {

    fun findByPodcastId(podcastId: String): List<Source>

    fun findByPodcastIdAndEnabled(podcastId: String, enabled: Boolean): List<Source>
}
