package com.aisummarypodcast.llm

import com.aisummarypodcast.store.ApiKeyCategory
import com.aisummarypodcast.store.LlmCacheRepository
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.user.UserProviderConfigService
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration

@Component
class ChatClientFactory(
    private val providerConfigService: UserProviderConfigService,
    private val llmCacheRepository: LlmCacheRepository,
    private val episodeHistoryRepository: EpisodeHistoryRepository
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
        toolBudget.register(HISTORY_LOOKUP_TOOL_NAME, HISTORY_LOOKUP_TOOL_CAP)
        val historyTool = HistoryLookupTool(
            episodeHistoryRepository = episodeHistoryRepository,
            toolBudget = toolBudget,
            podcastId = podcast.id,
            podcastName = podcast.name
        )
        return ChatClient.builder(buildCachingModel(userId, resolvedModel))
            .defaultTools(historyTool)
            .build()
    }

    private fun buildCachingModel(userId: String, resolvedModel: ResolvedModel): CachingChatModel {
        val config = providerConfigService.resolveConfig(userId, ApiKeyCategory.LLM, resolvedModel.provider)
            ?: throw IllegalStateException(
                "No provider config available for provider '${resolvedModel.provider}'. " +
                    "Configure a user provider for '${resolvedModel.provider}' or set the appropriate environment variable."
            )

        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setReadTimeout(Duration.ofMinutes(5))
        }
        val restClientBuilder = RestClient.builder().requestFactory(requestFactory)

        val openAiApi = OpenAiApi.builder()
            .apiKey(config.apiKey ?: "")
            .baseUrl(config.baseUrl)
            .restClientBuilder(restClientBuilder)
            .build()
        val chatModel = OpenAiChatModel.builder()
            .openAiApi(openAiApi)
            .build()
        return CachingChatModel(chatModel, llmCacheRepository)
    }
}
