package com.aisummarypodcast.llm

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
    private val researchService: ResearchService
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

    private fun buildCachingModel(userId: String, resolvedModel: ResolvedModel): CachingChatModel {
        val config = providerConfigService.resolveConfig(userId, ApiKeyCategory.LLM, resolvedModel.provider)
            ?: throw IllegalStateException(
                "No provider config available for provider '${resolvedModel.provider}'. " +
                    "Configure a user provider for '${resolvedModel.provider}' or set the appropriate environment variable."
            )

        // Generous request timeout: composition with deep-dive web research and history tool
        // calls over a large article set can run well past five minutes on slower models. The
        // dedup/filter stages bound their own output via maxTokens, so a longer ceiling here
        // only ever helps the long compose request and never lets a degenerate call hang.
        val openAiClient = buildOpenAiClient(config, Duration.ofMinutes(10))
        val chatModel = OpenAiChatModel.builder()
            .openAiClient(openAiClient)
            .build()
        return CachingChatModel(chatModel, llmCacheRepository)
    }
}
