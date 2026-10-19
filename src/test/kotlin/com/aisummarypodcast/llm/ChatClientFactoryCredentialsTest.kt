package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.BriefingProperties
import com.aisummarypodcast.config.EncryptionProperties
import com.aisummarypodcast.config.EpisodesProperties
import com.aisummarypodcast.config.FeedProperties
import com.aisummarypodcast.config.LlmProperties
import com.aisummarypodcast.store.ApiKeyCategory
import com.aisummarypodcast.store.LlmCacheRepository
import com.aisummarypodcast.user.ProviderConfig
import com.aisummarypodcast.user.UserProviderConfigService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

/**
 * Spring AI builds an async client for every chat model it is not handed one for, and looks for its
 * key only in the OPENAI_API_KEY environment variable. Without that variable every stage failed
 * with "At least one credential source must be specified", although the stored key was valid.
 */
class ChatClientFactoryCredentialsTest {

    private val providerConfigService = mockk<UserProviderConfigService>()

    private val factory = ChatClientFactory(
        providerConfigService = providerConfigService,
        llmCacheRepository = mockk<LlmCacheRepository>(),
        llmCallLogService = mockk<LlmCallLogService>(relaxed = true),
        generationStatsService = mockk<GenerationStatsService>(),
        appProperties = AppProperties(
            llm = LlmProperties(),
            briefing = BriefingProperties(),
            episodes = EpisodesProperties(),
            feed = FeedProperties(),
            encryption = EncryptionProperties(masterKey = "test")
        )
    )

    @Test
    fun `a model builds from the stored config alone, without OPENAI_API_KEY in the environment`() {
        assumeTrue(System.getenv("OPENAI_API_KEY") == null, "only meaningful without OPENAI_API_KEY set")
        every { providerConfigService.resolveConfig("user-1", ApiKeyCategory.LLM, "openrouter") } returns
            ProviderConfig("https://openrouter.ai/api", "sk-or-test")

        assertDoesNotThrow {
            factory.createForModel("user-1", ResolvedModel("openrouter", "deepseek/deepseek-v4.1-flash", null, PipelineStage.DEDUP))
        }
    }
}
