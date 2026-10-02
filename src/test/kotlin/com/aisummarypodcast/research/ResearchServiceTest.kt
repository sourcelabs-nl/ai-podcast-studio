package com.aisummarypodcast.research

import com.aisummarypodcast.store.ApiKeyCategory
import com.aisummarypodcast.user.ProviderConfig
import com.aisummarypodcast.user.UserProviderConfigService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper

class ResearchServiceTest {

    private val tavilyClient = mockk<TavilyClient>()
    private val cacheRepository = mockk<ResearchCacheRepository>(relaxUnitFun = true)
    private val objectMapper = JsonMapper.builder().build()
    private val providerConfigService = mockk<UserProviderConfigService>()
    private val restClientBuilder = RestClient.builder()
    private val mockServer = MockRestServiceServer.bindTo(restClientBuilder).build()
    private val service = ResearchService(tavilyClient, cacheRepository, objectMapper, providerConfigService, restClientBuilder)

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

    @Test
    fun `testTavily reports a missing key without calling Tavily`() {
        every { providerConfigService.resolveConfig("u1", ApiKeyCategory.RESEARCH, "tavily") } returns null

        val result = service.testTavily("u1")

        assertFalse(result.success)
        assertEquals("No Tavily API key configured (neither user-stored key nor TAVILY_API_KEY env var).", result.message)
        assertNull(result.resultCount)
        mockServer.verify()
    }

    @Test
    fun `testTavily reports the number of results on success`() {
        every { providerConfigService.resolveConfig("u1", ApiKeyCategory.RESEARCH, "tavily") } returns
            ProviderConfig("http://localhost", "tvly-test")
        mockServer.expect(requestTo("http://localhost/search"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer tvly-test"))
            .andRespond(withSuccess("""{"results":[{"title":"t","url":"https://u","content":"c"}]}""", org.springframework.http.MediaType.APPLICATION_JSON))

        val result = service.testTavily("u1")

        assertTrue(result.success)
        assertEquals("Tavily reachable. Returned 1 result(s).", result.message)
        assertEquals(1, result.resultCount)
        mockServer.verify()
    }

    @Test
    fun `testTavily reports a failing request as an unsuccessful result`() {
        every { providerConfigService.resolveConfig("u1", ApiKeyCategory.RESEARCH, "tavily") } returns
            ProviderConfig("http://localhost", "tvly-test")
        mockServer.expect(requestTo("http://localhost/search")).andRespond(withServerError())

        val result = service.testTavily("u1")

        assertFalse(result.success)
        assertTrue(result.message.startsWith("Tavily request failed: "), result.message)
    }
}
