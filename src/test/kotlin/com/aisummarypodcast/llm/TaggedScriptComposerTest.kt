package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.BriefingProperties
import com.aisummarypodcast.config.EncryptionProperties
import com.aisummarypodcast.config.EpisodesProperties
import com.aisummarypodcast.config.FeedProperties
import com.aisummarypodcast.config.LlmProperties
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.PodcastStyle
import com.aisummarypodcast.store.TtsProviderType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.metadata.ChatResponseMetadata
import org.springframework.ai.chat.metadata.DefaultUsage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import tools.jackson.databind.json.JsonMapper

class TaggedScriptComposerTest {

    private val appProperties = AppProperties(
        llm = LlmProperties(),
        briefing = BriefingProperties(targetWords = 1000),
        episodes = EpisodesProperties(),
        feed = FeedProperties(),
        encryption = EncryptionProperties(masterKey = "test-key")
    )
    private val model = ResolvedModel(provider = "openrouter", model = "test-model", cost = null, stage = PipelineStage.COMPOSE)
    private val chatModel = mockk<ChatModel>()
    private val chatClientFactory = mockk<ChatClientFactory>()
    private val composer = TaggedScriptComposer(appProperties, chatClientFactory, PromptVarietyPicker(), TopicOrderExtractor(JsonMapper.builder().build()))

    init {
        every { chatModel.getOptions() } returns mockk(relaxed = true)
        every { chatModel.getDefaultOptions() } returns mockk(relaxed = true)
        every { chatClientFactory.createForModel(any(), any(), any(), any()) } returns ChatClient.builder(chatModel).build()
    }

    private fun podcast(style: PodcastStyle) = Podcast(
        id = "p1", userId = "u1", name = "Tech Talk", topic = "tech",
        style = style, ttsProvider = TtsProviderType.ELEVENLABS, ttsVoices = emptyMap()
    )

    private fun request(kind: String, roles: Set<String>, style: PodcastStyle) = TaggedComposeRequest(
        kind = kind, roles = roles, articles = emptyList(), podcast = podcast(style),
        model = model, context = ComposeContext(), prompt = "Write a $kind."
    )

    private fun response(text: String, input: Int, output: Int) = ChatResponse(
        listOf(Generation(AssistantMessage(text))),
        ChatResponseMetadata.builder().usage(DefaultUsage(input, output)).build()
    )

    private fun assertComposes(kind: String, roles: Set<String>, style: PodcastStyle, script: String) = runBlocking {
        every { chatModel.call(any<Prompt>()) } returns response(script, 100, 200)

        val result = composer.compose(request(kind, roles, style))

        assertEquals(script, result.script)
        assertEquals(100, result.usage.inputTokens)
        assertEquals(200, result.usage.outputTokens)
    }

    @Test
    fun `a dialogue produces a composition result`() {
        assertComposes("dialogue", setOf("host", "cohost"), PodcastStyle.DIALOGUE, "<host>Hi</host><cohost>Hello</cohost>")
    }

    @Test
    fun `an interview produces a composition result`() {
        assertComposes("interview", setOf("interviewer", "expert"), PodcastStyle.INTERVIEW, "<interviewer>Hi</interviewer><expert>Hello</expert>")
    }

    @Test
    fun `discarded attempts are folded into the usage`() = runBlocking {
        every { chatModel.call(any<Prompt>()) } returnsMany listOf(
            response("Hi, no tags at all.", 1000, 10000),
            response("<interviewer>Hi</interviewer><expert>Hello</expert>", 1100, 9000)
        )

        val result = composer.compose(request("interview", setOf("interviewer", "expert"), PodcastStyle.INTERVIEW))

        assertTrue(result.script.startsWith("<interviewer>"))
        assertEquals(2100, result.usage.inputTokens)
        assertEquals(19000, result.usage.outputTokens)
    }

    @Test
    fun `a script that never uses the allowed roles is rejected`() {
        every { chatModel.call(any<Prompt>()) } returns response("No tags here.", 10, 10)

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { composer.compose(request("dialogue", setOf("host", "cohost"), PodcastStyle.DIALOGUE)) }
        }

        assertTrue(error.message.orEmpty().contains("Allowed roles: <host>, <cohost>"), error.message)
    }
}
