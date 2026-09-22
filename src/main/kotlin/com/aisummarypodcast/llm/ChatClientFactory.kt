package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.research.FOCUS_RESEARCH_TOOL_CAP
import com.aisummarypodcast.research.RESEARCH_TOOL_CAP
import com.aisummarypodcast.research.RESEARCH_TOOL_NAME
import com.aisummarypodcast.research.ResearchService
import com.aisummarypodcast.research.ResearchTool
import com.aisummarypodcast.store.ApiKeyCategory
import com.aisummarypodcast.store.EpisodeResearchSourceRepository
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
    private val llmCallLogService: LlmCallLogService,
    private val appProperties: AppProperties,
    private val researchSourceRepository: EpisodeResearchSourceRepository
) {

    /**
     * [useCache] is false for an evaluation run, so repeated identical prompts each reach the
     * model instead of replaying one cached answer. See [CachingChatModel].
     *
     * [attribution] names what the recorded telemetry belongs to. It defaults to naming nothing for
     * the callers that genuinely have neither an episode nor an article, such as preview and ad-hoc
     * source scoring; recording nothing there is the truthful answer rather than a gap.
     */
    fun createForModel(
        userId: String,
        resolvedModel: ResolvedModel,
        useCache: Boolean = true,
        attribution: LlmCallAttribution = LlmCallAttribution.NONE
    ): ChatClient {
        return ChatClient.builder(buildCachingModel(userId, resolvedModel, useCache, attribution)).build()
    }

    /**
     * Compose-stage entry point. Registers tools (currently just `searchPastEpisodes`) so the
     * LLM can look up prior coverage. Filter/score stages must keep using [createForModel] so
     * no tools are registered for them.
     *
     * A fresh [HistoryLookupTool] is bound to the supplied [toolBudget] and the podcast, so
     * concurrent compose calls each get their own counters and never query across podcasts.
     *
     * [context] carries whether this is a focus episode, which forces research on.
     */
    fun createForCompose(
        userId: String,
        resolvedModel: ResolvedModel,
        podcast: Podcast,
        toolBudget: ToolBudget,
        useCache: Boolean = true,
        attribution: LlmCallAttribution = LlmCallAttribution.NONE,
        context: ComposeContext = ComposeContext()
    ): ChatClient {
        val tools = buildComposeTools(userId, podcast, toolBudget, context)
        return ChatClient.builder(buildCachingModel(userId, resolvedModel, useCache, attribution))
            .defaultTools(*tools.toTypedArray())
            .build()
    }

    /**
     * Visible for testing: builds the list of compose-stage tools for [podcast] and registers
     * the corresponding caps with [toolBudget]. `searchPastEpisodes` is always present;
     * `webSearch` is added when [Podcast.deepDiveEnabled] is true, and always for a focus episode,
     * which gets the larger [FOCUS_RESEARCH_TOOL_CAP] and records its sources against the episode.
     */
    internal fun buildComposeTools(
        userId: String,
        podcast: Podcast,
        toolBudget: ToolBudget,
        context: ComposeContext = ComposeContext()
    ): List<Any> {
        toolBudget.register(HISTORY_LOOKUP_TOOL_NAME, HISTORY_LOOKUP_TOOL_CAP)
        val tools = mutableListOf<Any>(
            HistoryLookupTool(
                episodeHistoryRepository = episodeHistoryRepository,
                toolBudget = toolBudget,
                podcastId = podcast.id,
                podcastName = podcast.name
            )
        )
        if (podcast.deepDiveEnabled || context.focusResearchEnabled) {
            toolBudget.register(RESEARCH_TOOL_NAME, researchCapFor(context))
            tools += ResearchTool(
                researchService = researchService,
                toolBudget = toolBudget,
                userId = userId,
                podcastId = podcast.id,
                researchSourceRepository = researchSourceRepository,
                recordForEpisodeId = context.episodeId.takeIf { context.focusResearchEnabled }
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
    internal fun timeoutFor(stage: PipelineStage): Duration = stage.timeout(appProperties.llm.timeouts)

    private fun buildCachingModel(
        userId: String,
        resolvedModel: ResolvedModel,
        useCache: Boolean,
        attribution: LlmCallAttribution
    ): CachingChatModel {
        val config = providerConfigService.resolveConfig(userId, ApiKeyCategory.LLM, resolvedModel.provider)
            ?: throw IllegalStateException(
                "No provider config available for provider '${resolvedModel.provider}'. " +
                    "Configure a user provider for '${resolvedModel.provider}' or set the appropriate environment variable."
            )

        val openAiClient = buildOpenAiClient(config, timeoutFor(resolvedModel.stage))
        val chatModel = OpenAiChatModel.builder()
            .openAiClient(openAiClient)
            .build()
        return CachingChatModel(chatModel, llmCacheRepository, resolvedModel, llmCallLogService, useCache, attribution)
    }
}

/** The `webSearch` budget of one compose run: raised for a focus episode. */
internal fun researchCapFor(context: ComposeContext): Int =
    if (context.focusResearchEnabled) FOCUS_RESEARCH_TOOL_CAP else RESEARCH_TOOL_CAP
