package com.aisummarypodcast.store

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

interface PodcastPublicationTargetRepositoryCustom {
    fun upsert(target: PodcastPublicationTarget): PodcastPublicationTarget
}

@Repository
class PodcastPublicationTargetRepositoryCustomImpl(
    private val jdbcClient: JdbcClient
) : PodcastPublicationTargetRepositoryCustom {

    override fun upsert(target: PodcastPublicationTarget): PodcastPublicationTarget {
        jdbcClient.sql(
            """
            INSERT INTO podcast_publication_targets (podcast_id, target, config, enabled, auto_publish)
            VALUES (:podcastId, :target, :config, :enabled, :autoPublish)
            ON CONFLICT (podcast_id, target) DO UPDATE SET config = :config, enabled = :enabled, auto_publish = :autoPublish
            """.trimIndent()
        )
            .param("podcastId", target.podcastId)
            .param("target", target.target)
            .param("config", target.config)
            .param("enabled", if (target.enabled) 1 else 0)
            .param("autoPublish", if (target.autoPublish) 1 else 0)
            .update()

        return jdbcClient.sql(
            "SELECT * FROM podcast_publication_targets WHERE podcast_id = :podcastId AND target = :target"
        )
            .param("podcastId", target.podcastId)
            .param("target", target.target)
            .query { rs, _ ->
                PodcastPublicationTarget(
                    id = rs.getLong("id"),
                    podcastId = rs.getString("podcast_id"),
                    target = rs.getString("target"),
                    config = rs.getString("config"),
                    enabled = rs.getInt("enabled") == 1,
                    autoPublish = rs.getInt("auto_publish") == 1
                )
            }
            .single()
    }
}
