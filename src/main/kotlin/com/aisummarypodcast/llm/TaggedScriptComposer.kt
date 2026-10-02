package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import kotlin.time.measureTimedValue

/**
 * The compose call shared by the speaker-tagged script styles (dialogue and interview): one chat
 * call guarded by the role tag validation, whose result is cleaned up and given its provenance.
 * The prompt and the roles stay with the style's own composer.
 */
@Component
class TaggedScriptComposer(
    private val appProperties: AppProperties,
    private val chatClientFactory: ChatClientFactory,
    private val varietyPicker: PromptVarietyPicker,
    private val topicOrderExtractor: TopicOrderExtractor
) {

    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun compose(request: TaggedComposeRequest): CompositionResult {
        val (kind, roles, articles, podcast, model, context, prompt) = request
        log.info("[LLM] Composing {} from {} articles for podcast '{}' ({})", kind, articles.size, podcast.name, podcast.id)
        val chatClient = chatClientFactory.createForModel(
            podcast.userId, model,
            useCache = !context.bypassLlmCache, attribution = LlmCallAttribution(episodeId = context.episodeId)
        )

        val tagValidation = RoleTagValidationAdvisor(roles, topicOrderExtractor)

        val (result, elapsed) = measureTimedValue {
            val chatResponse = withContext(Dispatchers.IO) {
                chatClient.prompt()
                    .user(prompt)
                    .options(buildComposeOptions(model, podcast, appProperties))
                    .advisors(tagValidation)
                    .call()
                    .chatResponse()
            }

            val rawScript = chatResponse?.result?.output?.text
                ?: throw IllegalStateException("Empty response from LLM for $kind composition")

            val extraction = topicOrderExtractor.extract(rawScript)
            // The attempts the tag validation discarded were paid for too.
            val usage = tagValidation.discardedUsage.fold(TokenUsage.fromChatResponse(chatResponse), TokenUsage::plus)
            CompositionResult(
                script = cleanUpComposedScript(extraction.script, roles),
                usage = usage,
                topicOrder = extraction.topicOrder,
                provenance = EvaluationRunProvenance.of(
                    context = context,
                    prompt = prompt,
                    composeModel = model.model,
                    temperature = resolveTemperature(podcast, appProperties),
                    variety = varietyPicker.pick(podcast.id, context.episodeDate),
                    usage = usage
                )
            )
        }

        log.info("[LLM] {} composed for podcast '{}' ({}) — {} words in {}", kind.replaceFirstChar { it.uppercase() }, podcast.name, podcast.id, result.script.split("\\s+".toRegex()).size, elapsed)
        return result
    }
}
