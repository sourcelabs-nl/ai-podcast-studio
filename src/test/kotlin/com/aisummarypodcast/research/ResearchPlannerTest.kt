package com.aisummarypodcast.research

import com.aisummarypodcast.llm.ChatClientFactory
import com.aisummarypodcast.llm.LlmCallAttribution
import com.aisummarypodcast.llm.ModelResolver
import com.aisummarypodcast.llm.PipelineStage
import com.aisummarypodcast.llm.RESEARCH_PLAN_STAGE
import com.aisummarypodcast.llm.ResolvedModel
import com.aisummarypodcast.llm.RunConfig
import com.aisummarypodcast.store.Podcast
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import tools.jackson.databind.json.JsonMapper

/** The real ChatClient and output converter run over a mocked [ChatModel]. */
class ResearchPlannerTest {

    private val podcast = Podcast(id = "p1", userId = "u1", name = "Tech Daily", topic = "AI", deepDiveEnabled = true)
    private val filterModel = ResolvedModel("openrouter", "cheap-model", null, PipelineStage.FILTER)
    private val chatModel = mockk<ChatModel> {
        every { options } returns mockk(relaxed = true)
        every { defaultOptions } returns mockk(relaxed = true)
    }
    private val resolvedForClient = slot<ResolvedModel>()
    private val attribution = slot<LlmCallAttribution>()
    private val chatClientFactory = mockk<ChatClientFactory> {
        every { createForModel("u1", capture(resolvedForClient), any(), capture(attribution)) } returns
            ChatClient.builder(chatModel).build()
    }
    private val modelResolver = mockk<ModelResolver> {
        every { resolve(any(), PipelineStage.FILTER) } returns filterModel
    }
    private val planner = ResearchPlanner(chatClientFactory, modelResolver, JsonMapper.builder().build())
    private val runConfig = RunConfig(
        models = emptyMap(), reasoningEffort = emptyMap(),
        providerPreferences = com.aisummarypodcast.llm.ProviderPreferences.DEFAULT,
        targetWords = 1500, researchQueryCap = null, bypassLlmCache = false
    )

    private fun answer(text: String) {
        every { chatModel.call(any<Prompt>()) } returns ChatResponse(listOf(Generation(AssistantMessage(text))))
    }

    private val request = ResearchRequest(podcast, listOf("OpenAI o5 launch", "EU AI Act fines"), runConfig, episodeId = 230)

    @Test
    fun `returns the planned queries on the filter model under the research-plan stage`() {
        answer("""{"queries": ["o5 launch reactions", "EU AI Act first fines"]}""")

        val queries = planner.plan(request)

        assertEquals(listOf("o5 launch reactions", "EU AI Act first fines"), queries)
        assertEquals(RESEARCH_PLAN_STAGE, resolvedForClient.captured.telemetryStage)
        assertEquals(PipelineStage.FILTER, resolvedForClient.captured.stage)
        assertEquals(230L, attribution.captured.episodeId)
    }

    @Test
    fun `drops queries beyond the cap and blank or repeated ones`() {
        answer("""{"queries": ["a", "a", " ", "b", "c", "d"]}""")

        assertEquals(listOf("a", "b", "c"), planner.plan(request))
    }

    @Test
    fun `a focus episode may plan up to five queries`() {
        answer("""{"queries": ["1", "2", "3", "4", "5", "6"]}""")

        val queries = planner.plan(request.copy(subjects = listOf("Claude Opus 5.5"), focusEpisode = true))

        assertEquals(5, queries.size)
    }

    @Test
    fun `an unparseable answer falls back to the subjects`() {
        answer("I think you should search for o5.")

        assertEquals(listOf("OpenAI o5 launch", "EU AI Act fines"), planner.plan(request))
    }

    @Test
    fun `a failing call falls back to the subjects`() {
        every { chatModel.call(any<Prompt>()) } throws IllegalStateException("402")

        assertEquals(listOf("OpenAI o5 launch", "EU AI Act fines"), planner.plan(request))
        verify(exactly = 1) { chatModel.call(any<Prompt>()) }
    }

    @Test
    fun `the prompt names the subjects and the cap`() {
        val prompt = planner.buildPrompt(request)

        assertTrue(prompt.contains("- OpenAI o5 launch"))
        assertTrue(prompt.contains("at most 3 short web search queries"))
    }
}
