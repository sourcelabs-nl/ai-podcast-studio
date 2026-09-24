package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.store.ApiKeyCategory
import com.aisummarypodcast.store.LlmCacheRepository
import com.aisummarypodcast.user.ProviderConfig
import com.aisummarypodcast.user.UserProviderConfigService
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class ChatClientFactory(
    private val providerConfigService: UserProviderConfigService,
    private val llmCacheRepository: LlmCacheRepository,
    private val llmCallLogService: LlmCallLogService,
    private val generationStatsService: GenerationStatsService,
    private val appProperties: AppProperties
) {

    /**
     * Builds a client for [resolvedModel]. No tools are registered for any stage: compose receives
     * its research and history in the prompt (see `PreComposeResearchService`), so it completes in
     * one request instead of a tool round per lookup.
     *
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
     * Request timeout for [stage]. Only composition needs a long ceiling, having been observed
     * running 18m11s over a large article set, so it is the only stage that gets one. Sharing
     * that allowance with the others let a single hung scoring call stall an entire generation for 13 minutes while its 177 siblings each returned in seconds.
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
        return CachingChatModel(
            chatModel, llmCacheRepository, resolvedModel, llmCallLogService, useCache, attribution,
            generationStatsTracker(resolvedModel, config)
        )
    }

    /** Only OpenRouter reports per-request generation stats, and only a configured key can ask for them. */
    private fun generationStatsTracker(resolvedModel: ResolvedModel, config: ProviderConfig): GenerationStatsTracker? {
        if (resolvedModel.provider != OpenRouterRouting.PROVIDER) return null
        val apiKey = config.apiKey?.takeIf { it.isNotBlank() } ?: return null
        return GenerationStatsTracker(generationStatsService, GenerationStatsLookup(config.baseUrl, apiKey))
    }
}
