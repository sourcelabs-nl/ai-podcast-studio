package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.research.RESEARCH_TOOL_CAP
import com.aisummarypodcast.research.RESEARCH_TOOL_NAME
import com.aisummarypodcast.research.ResearchService
import com.aisummarypodcast.research.ResearchTool
import com.aisummarypodcast.store.ApiKeyCategory
import com.aisummarypodcast.store.LlmCacheRepository
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.user.UserProviderConfigService
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class ChatClientFactory(
    private val providerConfigService: UserProviderConfigService,
    private val llmCacheRepository: LlmCacheRepository,
    private val episodeHistoryRepository: EpisodeHistoryRepository,
    private val researchService: ResearchService,
    private val appProperties: AppProperties
) {

    fun createForModel(userId: String, resolvedModel: ResolvedModel): ChatClient {
        return ChatClient.builder(buildCachingModel(userId, resolvedModel)).build()
    }

    /**
     * Compose-stage entry point. Registers tools (currently just `searchPastEpisodes`) so the
     * LLM can look up prior coverage. Filter/score stages must keep using [createForModel] so
     * no tools are registered for them.
     *
     * A fresh [HistoryLookupTool] is bound to the supplied [toolBudget] and the podcast, so
     * concurrent compose calls each get their own counters and never query across podcasts.
     */
    fun createForCompose(
        userId: String,
        resolvedModel: ResolvedModel,
        podcast: Podcast,
        toolBudget: ToolBudget
    ): ChatClient {
        val tools = buildComposeTools(userId, podcast, toolBudget)
        return ChatClient.builder(buildCachingModel(userId, resolvedModel))
            .defaultTools(*tools.toTypedArray())
            .build()
    }

    /**
     * Visible for testing: builds the list of compose-stage tools for [podcast] and registers
     * the corresponding caps with [toolBudget]. `searchPastEpisodes` is always present;
     * `webSearch` is added only when [Podcast.deepDiveEnabled] is true.
     */
    internal fun buildComposeTools(userId: String, podcast: Podcast, toolBudget: ToolBudget): List<Any> {
        toolBudget.register(HISTORY_LOOKUP_TOOL_NAME, HISTORY_LOOKUP_TOOL_CAP)
        val tools = mutableListOf<Any>(
            HistoryLookupTool(
                episodeHistoryRepository = episodeHistoryRepository,
                toolBudget = toolBudget,
                podcastId = podcast.id,
                podcastName = podcast.name
            )
        )
        if (podcast.deepDiveEnabled) {
            toolBudget.register(RESEARCH_TOOL_NAME, RESEARCH_TOOL_CAP)
            tools += ResearchTool(
                researchService = researchService,
                toolBudget = toolBudget,
                userId = userId,
                podcastId = podcast.id
            )
        }
        return tools
    }

    /**
     * Request timeout for [stage]. Only composition needs a long ceiling — it has been observed
     * running 18m11s over a large article set with tool calls — so it is the only stage that gets
     * one. Sharing that allowance with the others let a single hung scoring call stall an entire
     * generation for 13 minutes while its 177 siblings each returned in seconds.
     */
    internal fun timeoutFor(stage: PipelineStage): Duration {
        val timeouts = appProperties.llm.timeouts
        return when (stage) {
            PipelineStage.FILTER -> timeouts.filter
            PipelineStage.DEDUP -> timeouts.dedup
            PipelineStage.COMPOSE -> timeouts.compose
        }
    }

    private fun buildCachingModel(userId: String, resolvedModel: ResolvedModel): CachingChatModel {
        val config = providerConfigService.resolveConfig(userId, ApiKeyCategory.LLM, resolvedModel.provider)
            ?: throw IllegalStateException(
                "No provider config available for provider '${resolvedModel.provider}'. " +
                    "Configure a user provider for '${resolvedModel.provider}' or set the appropriate environment variable."
            )

        val openAiClient = buildOpenAiClient(config, timeoutFor(resolvedModel.stage))
        val chatModel = OpenAiChatModel.builder()
            .openAiClient(openAiClient)
            .build()
        return CachingChatModel(chatModel, llmCacheRepository)
    }
}
