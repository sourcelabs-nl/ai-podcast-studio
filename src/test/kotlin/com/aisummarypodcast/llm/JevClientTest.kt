package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.BriefingProperties
import com.aisummarypodcast.config.ComposeProperties
import com.aisummarypodcast.config.EncryptionProperties
import com.aisummarypodcast.config.EpisodesProperties
import com.aisummarypodcast.config.FeedProperties
import com.aisummarypodcast.config.LlmProperties
import com.aisummarypodcast.store.ApiKeyCategory
import com.aisummarypodcast.user.ProviderConfig
import com.aisummarypodcast.user.UserProviderConfigService
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import org.springframework.web.client.ResourceAccessException
import java.time.Duration

private const val URL = "http://localhost/api/alpha/decisions"

private val CALLER = JevCaller(stage = DEDUP_GATE_STAGE, episodeId = 42L)

/** Long enough that a duration including it would be unmistakable. */
private val RETRY_WAIT = Duration.ofMillis(300)

class JevClientTest {

    private val providerConfigService = mockk<UserProviderConfigService>()
    private val llmCallLogService = mockk<LlmCallLogService>(relaxed = true)
    private val restClientBuilder = RestClient.builder()
    private val appProperties = AppProperties(
        llm = LlmProperties(),
        briefing = BriefingProperties(),
        episodes = EpisodesProperties(),
        feed = FeedProperties(),
        encryption = EncryptionProperties(masterKey = "test"),
        compose = ComposeProperties(maxArticles = 40)
    )
    private lateinit var mockServer: MockRestServiceServer
    private lateinit var client: JevClient

    private val endpoint = JevEndpoint(URL, "typesafe/jev-1.13")

    /**
     * The application's retry config for this instance, restated so the tests prove the
     * classification rather than a registry that retries everything.
     */
    private fun jevRetryRegistry(maxAttempts: Int = 3) = RetryRegistry.of(
        RetryConfig.custom<Any>()
            .maxAttempts(maxAttempts)
            .waitDuration(Duration.ofMillis(1))
            .retryExceptions(JevTransientException::class.java, ResourceAccessException::class.java)
            .build()
    )

    @BeforeEach
    fun setup() {
        // Constructed first: the client sets its own request factory in init, which would replace
        // the mock server's factory if the server were bound before it.
        client = JevClient(providerConfigService, restClientBuilder, llmCallLogService, jevRetryRegistry(), appProperties)
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build()
        every {
            providerConfigService.resolveConfig("u1", ApiKeyCategory.LLM, "openrouter")
        } returns ProviderConfig("https://openrouter.ai/api/v1", "sk-or-test")
    }

    @AfterEach
    fun reset() = mockServer.reset()

    @Test
    fun `sends one request carrying the shared state and every question`() {
        mockServer.expect(requestTo(URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer sk-or-test"))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.model").value("typesafe/jev-1.13"))
            .andExpect(jsonPath("$.state.covered_topics[0]").value("Opus 5 release"))
            .andExpect(jsonPath("$.questions.a1.type").value("noul"))
            .andExpect(jsonPath("$.questions.a2.instructions").value("Second question"))
            .andRespond(
                withSuccess(
                    """
                    {
                      "answers": {"a1": {"type": "noul", "noul": 0.93}, "a2": {"type": "noul", "noul": 0.11}},
                      "usage": {"input_tokens": 13036, "output_tokens": 40, "cost": 0.00055}
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON
                )
            )

        val answers = client.ask(
            userId = "u1",
            state = mapOf("covered_topics" to listOf("Opus 5 release")),
            questions = mapOf(
                "a1" to JevNoulQuestion("First question"),
                "a2" to JevNoulQuestion("Second question")
            ),
            endpoint = endpoint,
            caller = CALLER
        )

        assertEquals(mapOf("a1" to 0.93, "a2" to 0.11), answers.noul)
        assertEquals(13036, answers.inputTokens)
        assertEquals(0.00055, answers.reportedCostUsd)
        mockServer.verify()
    }

    @Test
    fun `drops an answer that carries no value rather than defaulting it`() {
        mockServer.expect(requestTo(URL)).andRespond(
            withSuccess(
                """{"answers": {"a1": {"type": "noul", "noul": 0.9}, "a2": {"type": "noul"}}}""",
                MediaType.APPLICATION_JSON
            )
        )

        val answers = client.ask("u1", emptyMap<String, String>(), questions("a1", "a2"), endpoint, CALLER)

        assertEquals(mapOf("a1" to 0.9), answers.noul)
    }

    @Test
    fun `a rejected request yields no answers and no cost`() {
        mockServer.expect(requestTo(URL)).andRespond(withBadRequest())

        val answers = client.ask("u1", emptyMap<String, String>(), questions("a1"), endpoint, CALLER)

        assertTrue(answers.noul.isEmpty())
        assertNull(answers.reportedCostUsd)
    }

    @Test
    fun `an unparseable body yields no answers`() {
        mockServer.expect(requestTo(URL))
            .andRespond(withSuccess("not json at all", MediaType.APPLICATION_JSON))

        assertTrue(client.ask("u1", emptyMap<String, String>(), questions("a1"), endpoint, CALLER).noul.isEmpty())
    }

    @Test
    fun `a missing credential asks nothing at all`() {
        every {
            providerConfigService.resolveConfig("u1", ApiKeyCategory.LLM, "openrouter")
        } returns null

        val answers = client.ask("u1", emptyMap<String, String>(), questions("a1"), endpoint, CALLER)

        assertTrue(answers.noul.isEmpty())
        // No expectation was registered, so verify also proves no request left the client.
        mockServer.verify()
    }

    @Test
    fun `no questions makes no request`() {
        val answers = client.ask("u1", emptyMap<String, String>(), emptyMap(), endpoint, CALLER)

        assertTrue(answers.noul.isEmpty())
        mockServer.verify()
    }

    @Test
    fun `a 529 system_overloaded is retried and can succeed`() {
        // The status the live endpoint returned to both chunks of a real pipeline run.
        mockServer.expect(requestTo(URL))
            .andRespond(withStatus(HttpStatusCode.valueOf(529)).body("""{"detail":{"error_type":"system_overloaded"}}"""))
        mockServer.expect(requestTo(URL))
            .andRespond(withSuccess("""{"answers":{"a1":{"noul":0.9}}}""", MediaType.APPLICATION_JSON))

        val answers = client.ask("u1", emptyMap<String, String>(), questions("a1"), endpoint, CALLER)

        assertEquals(mapOf("a1" to 0.9), answers.noul)
        // Both attempts are charged, so both are counted: the caller costs the gate per request.
        assertEquals(2, answers.requests)
        mockServer.verify()
    }

    @Test
    fun `a 503 is retried`() {
        mockServer.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("no healthy upstream"))
        mockServer.expect(requestTo(URL))
            .andRespond(withSuccess("""{"answers":{"a1":{"noul":0.2}}}""", MediaType.APPLICATION_JSON))

        assertEquals(mapOf("a1" to 0.2), client.ask("u1", emptyMap<String, String>(), questions("a1"), endpoint, CALLER).noul)
        mockServer.verify()
    }

    @Test
    fun `exhausting the retries yields no answers rather than throwing`() {
        repeat(3) {
            mockServer.expect(requestTo(URL)).andRespond(withServerError())
        }

        val answers = client.ask("u1", emptyMap<String, String>(), questions("a1"), endpoint, CALLER)

        assertTrue(answers.noul.isEmpty())
        assertNull(answers.reportedCostUsd)
        mockServer.verify()
    }

    @Test
    fun `a rejected request is not retried`() {
        // An oversized batch and a bad credential fail identically every time, so spending the
        // attempts on them only delays the stage.
        mockServer.expect(requestTo(URL))
            .andRespond(withBadRequest().body("""{"detail":{"error_type":"max_tokens_exceeded"}}"""))

        assertTrue(client.ask("u1", emptyMap<String, String>(), questions("a1"), endpoint, CALLER).noul.isEmpty())
        // Exactly one expectation was set, so verify proves no second attempt was made.
        mockServer.verify()
    }

    private fun questions(vararg keys: String) = keys.associateWith { JevNoulQuestion("Is $it covered?") }

    @Test
    fun `a successful call is recorded as one request`() {
        val record = slot<LlmCallRecord>()
        every { llmCallLogService.record(capture(record)) } returns 1L
        mockServer.expect(requestTo(URL)).andRespond(
            withSuccess(
                """
                {
                  "answers": {"a1": {"type": "noul", "noul": 0.9}},
                  "usage": {"input_tokens": 374, "cost": 0.000015708}
                }
                """.trimIndent(),
                MediaType.APPLICATION_JSON
            )
        )

        client.ask("u1", emptyMap<String, String>(), questions("a1"), endpoint, CALLER)

        with(record.captured) {
            assertEquals(DEDUP_GATE_STAGE, stage)
            assertEquals(42L, attribution.episodeId)
            // The credential's provider, not the `provider` the response names.
            assertEquals("openrouter", provider)
            assertEquals("typesafe/jev-1.13", model)
            assertEquals(374, inputTokens)
            // Billed at zero and often omitted: a count invented here would read as the provider's.
            assertEquals(0, outputTokens)
            assertEquals(0.000015708, reportedCostUsd)
            assertEquals(LlmCallOutcome.OK, outcome)
            assertFalse(cacheHit)
            assertNull(errorType)
        }
    }

    @Test
    fun `a retried transient failure is recorded once per attempt`() {
        val records = mutableListOf<LlmCallRecord>()
        every { llmCallLogService.record(capture(records)) } returns 1L
        // Rebuilt with a wait long enough to show up in a duration that wrongly included it.
        client = JevClient(
            providerConfigService,
            restClientBuilder,
            llmCallLogService,
            RetryRegistry.of(
                RetryConfig.custom<Any>()
                    .maxAttempts(3)
                    .waitDuration(RETRY_WAIT)
                    .retryExceptions(JevTransientException::class.java)
                    .build()
            ),
            appProperties
        )
        // Rebound after the client, which sets its own request factory in init.
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build()
        mockServer.expect(requestTo(URL)).andRespond(withStatus(HttpStatusCode.valueOf(529)))
        mockServer.expect(requestTo(URL)).andRespond(
            withSuccess("""{"answers": {"a1": {"type": "noul", "noul": 0.9}}}""", MediaType.APPLICATION_JSON)
        )

        client.ask("u1", emptyMap<String, String>(), questions("a1"), endpoint, CALLER)

        assertEquals(2, records.size)
        assertEquals(LlmCallOutcome.ERROR, records[0].outcome)
        assertEquals("JevTransientException", records[0].errorType)
        assertEquals(LlmCallOutcome.OK, records[1].outcome)
        // The retry's wait falls between the attempts and so inside neither duration.
        assertTrue(records.all { it.duration.inWholeMilliseconds < RETRY_WAIT.toMillis() })
    }

    @Test
    fun `a rejected request is recorded as a failure carrying its kind`() {
        val record = slot<LlmCallRecord>()
        every { llmCallLogService.record(capture(record)) } returns 1L
        mockServer.expect(requestTo(URL)).andRespond(withBadRequest())

        client.ask("u1", emptyMap<String, String>(), questions("a1"), endpoint, CALLER)

        assertEquals(LlmCallOutcome.ERROR, record.captured.outcome)
        assertEquals("IllegalStateException", record.captured.errorType)
        assertEquals(0, record.captured.inputTokens)
        assertNull(record.captured.reportedCostUsd)
    }

    @Test
    fun `a request with no credential records nothing`() {
        every {
            providerConfigService.resolveConfig("u1", ApiKeyCategory.LLM, "openrouter")
        } returns null

        client.ask("u1", emptyMap<String, String>(), questions("a1"), endpoint, CALLER)

        // Nothing was asked of the provider, so there is no request to record.
        verify(exactly = 0) { llmCallLogService.record(any()) }
    }

    @Test
    fun `a failure to record does not change what the call returns`() {
        every { llmCallLogService.record(any()) } throws IllegalStateException("log is down")
        mockServer.expect(requestTo(URL)).andRespond(
            withSuccess("""{"answers": {"a1": {"type": "noul", "noul": 0.9}}}""", MediaType.APPLICATION_JSON)
        )

        val answers = client.ask("u1", emptyMap<String, String>(), questions("a1"), endpoint, CALLER)

        assertEquals(mapOf("a1" to 0.9), answers.noul)
    }

    @Test
    fun `a question asked outside an episode is recorded without one`() {
        val record = slot<LlmCallRecord>()
        every { llmCallLogService.record(capture(record)) } returns 1L
        mockServer.expect(requestTo(URL)).andRespond(
            withSuccess("""{"answers": {"a1": {"type": "noul", "noul": 0.9}}}""", MediaType.APPLICATION_JSON)
        )

        client.ask(
            "u1", emptyMap<String, String>(), questions("a1"), endpoint,
            JevCaller(stage = DEDUP_GATE_STAGE)
        )

        assertNull(record.captured.attribution.episodeId)
        assertEquals(DEDUP_GATE_STAGE, record.captured.stage)
    }
}
