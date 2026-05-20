package com.aisummarypodcast.research

import com.aisummarypodcast.store.ApiKeyCategory
import com.aisummarypodcast.user.ProviderConfig
import com.aisummarypodcast.user.UserProviderConfigService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class TavilyClientTest {

    private val providerConfigService = mockk<UserProviderConfigService>()
    private val restClientBuilder = RestClient.builder()
    private lateinit var mockServer: MockRestServiceServer
    private lateinit var client: TavilyClient

    @BeforeEach
    fun setup() {
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build()
        client = TavilyClient(providerConfigService, restClientBuilder)
        every {
            providerConfigService.resolveConfig("u1", ApiKeyCategory.RESEARCH, "tavily")
        } returns ProviderConfig("http://localhost", "tvly-test")
    }

    @AfterEach
    fun reset() {
        mockServer.reset()
    }

    @Test
    fun `sends POST with bearer auth, snake_case body, and parses results`() {
        mockServer.expect(requestTo("http://localhost/search"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer tvly-test"))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.query").value("speckit launch"))
            .andExpect(jsonPath("$.max_results").value(5))
            .andExpect(jsonPath("$.search_depth").value("basic"))
            .andRespond(
                withSuccess(
                    """
                    {
                      "results": [
                        {"title": "Speckit announces release", "url": "https://example.com/a", "content": "snippet a"},
                        {"title": "Reaction to Speckit", "url": "https://example.com/b", "content": "snippet b"}
                      ]
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON
                )
            )

        val response = client.search("u1", "speckit launch", 5)

        assertEquals(2, response.results.size)
        assertEquals("Speckit announces release", response.results[0].title)
        assertEquals("https://example.com/a", response.results[0].url)
        mockServer.verify()
    }

    @Test
    fun `returns empty results on server error`() {
        mockServer.expect(requestTo("http://localhost/search"))
            .andRespond(withServerError())

        val response = client.search("u1", "anything", 5)

        assertTrue(response.results.isEmpty())
    }

    @Test
    fun `returns empty results when api key is missing`() {
        every {
            providerConfigService.resolveConfig("u1", ApiKeyCategory.RESEARCH, "tavily")
        } returns null

        val response = client.search("u1", "anything", 5)

        assertTrue(response.results.isEmpty())
    }
}
