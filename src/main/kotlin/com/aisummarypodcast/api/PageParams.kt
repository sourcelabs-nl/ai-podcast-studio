package com.aisummarypodcast.api

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort

/** A validated page request, shared by every paged listing endpoint. */
data class PageParams(val page: Int, val pageSize: Int) {

    fun toPageable(sort: Sort): Pageable = PageRequest.of(page, pageSize, sort)

    companion object {
        const val MAX_PAGE_SIZE = 200

        /** Throws [InvalidRequestException] when [page] or [pageSize] is out of range. */
        fun of(page: Int, pageSize: Int): PageParams {
            if (page < 0) throw InvalidRequestException("page must be >= 0")
            if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) throw InvalidRequestException("pageSize must be in [1, $MAX_PAGE_SIZE]")
            return PageParams(page, pageSize)
        }
    }
}
