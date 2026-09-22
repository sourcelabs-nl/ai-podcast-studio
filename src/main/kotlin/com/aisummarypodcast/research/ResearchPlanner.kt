package com.aisummarypodcast.research

import com.aisummarypodcast.llm.ChatClientFactory
import com.aisummarypodcast.llm.LlmCallAttribution
import com.aisummarypodcast.llm.ModelResolver
import com.aisummarypodcast.llm.OpenRouterRouting
import com.aisummarypodcast.llm.PipelineStage
import com.aisummarypodcast.llm.RESEARCH_PLAN_STAGE
import com.aisummarypodcast.llm.withRoutingAndReasoning
import org.slf4j.LoggerFactory
import org.springframework.ai.converter.BeanOutputConverter
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import kotlin.coroutines.cancellation.CancellationException

/** A few short queries fit well inside this; the cap keeps a degenerate answer cheap. */
private const val PLAN_MAX_OUTPUT_TOKENS = 600

/**
 * Turns an episode's research subjects into web search queries with one call on the podcast's
 * filter model, recorded under [RESEARCH_PLAN_STAGE].
 *
 * The parse is hand-rolled rather than self-correcting, because the plan has a salvage rule: when
 * the call fails or its answer is unusable, the subjects themselves are searched. That is always a
 * usable plan, so a retry would only buy nicer wording at the cost of a second call, and the plan
 * must never fail the episode.
 */
@Component
class ResearchPlanner(
    private val chatClientFactory: ChatClientFactory,
    private val modelResolver: ModelResolver,
    private val jsonMapper: JsonMapper
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** At most [ResearchRequest.queryCap] distinct, non-blank queries. Blocking. */
    fun plan(request: ResearchRequest): List<String> {
        val cap = request.queryCap
        val planned = try {
            requestPlan(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn(
                "[Research] Plan call failed for podcast '{}' ({}), searching the subjects instead: {}",
                request.podcast.name, request.podcast.id, e.message
            )
            emptyList()
        }
        val queries = planned.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(cap)
        if (queries.isNotEmpty()) return queries
        return request.subjects.take(cap)
    }

    private fun requestPlan(request: ResearchRequest): List<String> {
        val filterModel = modelResolver.resolve(request.podcast, PipelineStage.FILTER)
            .copy(telemetryStage = RESEARCH_PLAN_STAGE)
        val chatClient = chatClientFactory.createForModel(
            request.podcast.userId,
            filterModel,
            attribution = LlmCallAttribution(episodeId = request.episodeId)
        )
        val converter = BeanOutputConverter(ResearchQueryPlan::class.java, jsonMapper)
        val plan = chatClient.prompt()
            .user(buildPrompt(request))
            .options(
                OpenAiChatOptions.builder()
                    .model(filterModel.model)
                    .temperature(0.3)
                    .maxTokens(PLAN_MAX_OUTPUT_TOKENS)
                    .withRoutingAndReasoning(filterModel.provider, OpenRouterRouting.NO_REASONING)
            )
            .call()
            .entity(converter)
        return plan?.queries.orEmpty()
    }

    internal fun buildPrompt(request: ResearchRequest): String {
        val subjects = request.subjects.joinToString("\n") { "- $it" }
        val scope = if (request.focusEpisode) {
            "This is a special episode about one subject only: \"${request.subjects.first()}\". " +
                "Spread the queries over distinct angles of that subject, such as the announcement itself, " +
                "reactions, numbers or benchmarks, and what it means for the field."
        } else {
            "Pick the most newsworthy stories among these and write one query for each; skip stories that " +
                "need no outside context."
        }
        return """
            |You plan web research for an episode of the podcast "${request.podcast.name}" about ${request.podcast.topic}.
            |The episode covers these stories:
            |$subjects
            |
            |$scope
            |Write at most ${request.queryCap} short web search queries (a few keywords each) that would find
            |background, related developments or dissenting takes the articles may lack.
            |
            |Respond with a JSON object of the form {"queries": ["first query", "second query"]} and nothing else.
        """.trimMargin()
    }
}
