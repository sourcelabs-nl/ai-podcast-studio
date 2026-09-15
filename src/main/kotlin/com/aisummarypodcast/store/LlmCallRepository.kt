package com.aisummarypodcast.store

import org.springframework.data.repository.CrudRepository

interface LlmCallRepository : CrudRepository<LlmCall, Long>, LlmCallRepositoryCustom {

    /** `started_at` holds ISO-8601 instants, which sort lexically, so the cutoff compares directly. */
    fun deleteByStartedAtLessThan(cutoff: String)
}
