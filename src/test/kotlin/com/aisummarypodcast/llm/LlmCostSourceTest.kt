package com.aisummarypodcast.llm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LlmCostSourceTest {

    @Test
    fun `all stages reported aggregates to API`() {
        assertEquals(
            LlmCostSource.API,
            LlmCostSource.aggregate(listOf(LlmCostSource.API, LlmCostSource.API, LlmCostSource.API))
        )
    }

    @Test
    fun `all stages replayed aggregates to API_CACHED`() {
        assertEquals(
            LlmCostSource.API_CACHED,
            LlmCostSource.aggregate(listOf(LlmCostSource.API_CACHED, LlmCostSource.API_CACHED))
        )
    }

    @Test
    fun `no stage reported aggregates to TABLE`() {
        assertEquals(
            LlmCostSource.TABLE,
            LlmCostSource.aggregate(listOf(LlmCostSource.TABLE, LlmCostSource.TABLE))
        )
    }

    @Test
    fun `reported and estimated stages aggregate to MIXED`() {
        assertEquals(
            LlmCostSource.MIXED,
            LlmCostSource.aggregate(listOf(LlmCostSource.API, LlmCostSource.TABLE))
        )
    }

    @Test
    fun `a MIXED stage makes the whole episode MIXED`() {
        assertEquals(
            LlmCostSource.MIXED,
            LlmCostSource.aggregate(listOf(LlmCostSource.MIXED, LlmCostSource.API))
        )
    }

    @Test
    fun `a stage that resolved nothing alongside a reported stage is MIXED`() {
        assertEquals(
            LlmCostSource.MIXED,
            LlmCostSource.aggregate(listOf(LlmCostSource.API, LlmCostSource.UNKNOWN))
        )
    }

    @Test
    fun `nothing resolved anywhere aggregates to UNKNOWN`() {
        assertEquals(
            LlmCostSource.UNKNOWN,
            LlmCostSource.aggregate(listOf(LlmCostSource.UNKNOWN, LlmCostSource.UNKNOWN))
        )
    }

    @Test
    fun `live and replayed stages aggregate to API since both are provider-reported`() {
        assertEquals(
            LlmCostSource.API,
            LlmCostSource.aggregate(listOf(LlmCostSource.API, LlmCostSource.API_CACHED))
        )
    }

    @Test
    fun `no contributing stage leaves the source null`() {
        assertNull(LlmCostSource.aggregate(emptyList()))
    }
}
