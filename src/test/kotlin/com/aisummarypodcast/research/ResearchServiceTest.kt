package com.aisummarypodcast.research

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class ResearchServiceTest {

    private val tavilyClient = mockk<TavilyClient>()
    private val cacheRepository = mockk<ResearchCacheRepository>(relaxUnitFun = true)
    private val objectMapper = JsonMapper.builder().build()
    private val service = ResearchService(tavilyClient, cacheRepository, objectMapper)

    @Test
    fun `cache hit returns cached response without calling Tavily`() {
        val cachedJson = """{"results":[{"title":"cached","url":"https://x","content":"cached content"}]}"""
        every { cacheRepository.find("speckit", 5) } returns cachedJson

        val response = service.search("u1", "speckit", 5)

        assertEquals(1, response.results.size)
        assertEquals("cached", response.results[0].title)
        verify(exactly = 0) { tavilyClient.search(any(), any(), any()) }
    }

    @Test
    fun `cache miss fetches from Tavily and caches the response`() {
        every { cacheRepository.find("speckit", 5) } returns null
        every { tavilyClient.search("u1", "speckit", 5) } returns TavilyResponse(
            results = listOf(TavilyResult("t", "https://u", "c"))
        )

        val response = service.search("u1", "speckit", 5)

        assertEquals(1, response.results.size)
        verify { cacheRepository.save("speckit", 5, any()) }
    }

    @Test
    fun `empty Tavily response is not cached`() {
        every { cacheRepository.find("speckit", 5) } returns null
        every { tavilyClient.search("u1", "speckit", 5) } returns TavilyResponse(results = emptyList())

        service.search("u1", "speckit", 5)

        verify(exactly = 0) { cacheRepository.save(any(), any(), any()) }
    }
}
