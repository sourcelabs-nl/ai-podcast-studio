package com.aisummarypodcast.api

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.Sort
import kotlin.test.assertEquals

class PageParamsTest {

    @Test
    fun `accepts the boundaries`() {
        assertEquals(PageParams(0, 1), PageParams.of(0, 1))
        assertEquals(PageParams(5, 200), PageParams.of(5, 200))
    }

    @Test
    fun `rejects a negative page`() {
        val e = assertThrows<InvalidRequestException> { PageParams.of(-1, 20) }
        assertEquals("page must be >= 0", e.message)
    }

    @Test
    fun `rejects a page size outside 1 to 200`() {
        listOf(0, 201).forEach { size ->
            val e = assertThrows<InvalidRequestException> { PageParams.of(0, size) }
            assertEquals("pageSize must be in [1, 200]", e.message)
        }
    }

    @Test
    fun `builds a pageable`() {
        val pageable = PageParams.of(2, 10).toPageable(Sort.by("id"))
        assertEquals(2, pageable.pageNumber)
        assertEquals(10, pageable.pageSize)
    }
}
