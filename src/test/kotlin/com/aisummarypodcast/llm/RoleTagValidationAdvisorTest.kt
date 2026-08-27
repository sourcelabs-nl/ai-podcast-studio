package com.aisummarypodcast.llm

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt

/**
 * Only [ChatModel] is mocked; [ChatClient] and the real Spring AI advisor chain run for real, so
 * this exercises the actual retry mechanics ([org.springframework.ai.chat.client.advisor.api.CallAdvisorChain.copy],
 * [Prompt.augmentUserMessage]) rather than a hand-mocked approximation of them.
 */
class RoleTagValidationAdvisorTest {

    private val allowedRoles = setOf("interviewer", "expert")

    private fun response(text: String) = ChatResponse(listOf(Generation(AssistantMessage(text))))

    private fun buildChatClient(chatModel: ChatModel): ChatClient {
        every { chatModel.getOptions() } returns mockk(relaxed = true)
        every { chatModel.getDefaultOptions() } returns mockk(relaxed = true)
        return ChatClient.builder(chatModel).build()
    }

    @Test
    fun `valid roles pass through without retrying`() {
        val chatModel = mockk<ChatModel>()
        every { chatModel.call(any<Prompt>()) } returns response("<interviewer>Hi</interviewer><expert>Hello</expert>")

        val result = buildChatClient(chatModel).prompt()
            .user("Write a script.")
            .advisors(RoleTagValidationAdvisor(allowedRoles))
            .call()
            .content()

        assertEquals("<interviewer>Hi</interviewer><expert>Hello</expert>", result)
        verify(exactly = 1) { chatModel.call(any<Prompt>()) }
    }

    @Test
    fun `invalid role retries and self-corrects`() {
        val chatModel = mockk<ChatModel>()
        val invalid = "<function_results>leaked tool output</function_results><interviewer>Hi</interviewer>"
        val valid = "<interviewer>Hi</interviewer><expert>Hello</expert>"
        val promptSlot = mutableListOf<Prompt>()
        every { chatModel.call(capture(promptSlot)) } returnsMany listOf(response(invalid), response(valid))

        val result = buildChatClient(chatModel).prompt()
            .user("Write a script.")
            .advisors(RoleTagValidationAdvisor(allowedRoles))
            .call()
            .content()

        assertEquals(valid, result)
        verify(exactly = 2) { chatModel.call(any<Prompt>()) }

        val retryPromptText = promptSlot[1].instructions.joinToString("\n") { it.text ?: "" }
        assertTrue(retryPromptText.contains("function_results"))
        assertTrue(retryPromptText.contains("interviewer"))
        assertTrue(retryPromptText.contains("expert"))
    }

    @Test
    fun `response with pending tool calls bypasses role validation`() {
        val chatModel = mockk<ChatModel>()
        val toolCallMessage = AssistantMessage.builder()
            .content("<function_results>partial</function_results>")
            .toolCalls(listOf(AssistantMessage.ToolCall("id-1", "function", "searchPastEpisodes", "{}")))
            .build()
        every { chatModel.call(any<Prompt>()) } returns ChatResponse(listOf(Generation(toolCallMessage)))

        val result = buildChatClient(chatModel).prompt()
            .user("Write a script.")
            .advisors(RoleTagValidationAdvisor(allowedRoles))
            .call()
            .content()

        // Tool-call round trips are resolved internally by Spring AI before a final text
        // response reaches this advisor; a response that still carries tool calls is passed
        // through untouched rather than validated, since its text is not the final script.
        assertEquals("<function_results>partial</function_results>", result)
        verify(exactly = 1) { chatModel.call(any<Prompt>()) }
    }

    @Test
    fun `untagged script retries and self-corrects`() {
        val chatModel = mockk<ChatModel>()
        // Episode 187: a correctly alternating interview with every speaker tag omitted. No tag
        // means no invalid role, so a wrong-tag-only check read this as valid and TTS threw on it.
        val untagged = "Picture this. A pull request lands at Bloomberg.\n\nGreat to be here, that did not exaggerate."
        val valid = "<interviewer>Hi</interviewer><expert>Hello</expert>"
        val promptSlot = mutableListOf<Prompt>()
        every { chatModel.call(capture(promptSlot)) } returnsMany listOf(response(untagged), response(valid))

        val result = buildChatClient(chatModel).prompt()
            .user("Write a script.")
            .advisors(RoleTagValidationAdvisor(allowedRoles))
            .call()
            .content()

        assertEquals(valid, result)
        verify(exactly = 2) { chatModel.call(any<Prompt>()) }

        val retryPromptText = promptSlot[1].instructions.joinToString("\n") { it.text ?: "" }
        assertTrue(retryPromptText.contains("no speaker tags at all"))
        assertTrue(retryPromptText.contains("interviewer"))
        assertTrue(retryPromptText.contains("expert"))
    }

    @Test
    fun `square-bracketed opener is not treated as untagged`() {
        val chatModel = mockk<ChatModel>()
        // normalizeSquareBracketSpeakerTags recovers this downstream, so retrying would burn a
        // compose on a script the pipeline can already voice.
        val bracketed = "[interviewer]Hi there.</interviewer><expert>Hello</expert>"
        every { chatModel.call(any<Prompt>()) } returns response(bracketed)

        val result = buildChatClient(chatModel).prompt()
            .user("Write a script.")
            .advisors(RoleTagValidationAdvisor(allowedRoles))
            .call()
            .content()

        assertEquals(bracketed, result)
        verify(exactly = 1) { chatModel.call(any<Prompt>()) }
    }

    @Test
    fun `throws after exhausting retries on an untagged script`() {
        val chatModel = mockk<ChatModel>()
        every { chatModel.call(any<Prompt>()) } returns response("A plain paragraph with no tags.")

        val ex = assertThrows(IllegalStateException::class.java) {
            buildChatClient(chatModel).prompt()
                .user("Write a script.")
                .advisors(RoleTagValidationAdvisor(allowedRoles, maxRetries = 2))
                .call()
                .content()
        }

        assertTrue(ex.message!!.contains("no speaker tags"))
        verify(exactly = 3) { chatModel.call(any<Prompt>()) }
    }

    @Test
    fun `throws after exhausting retries`() {
        val chatModel = mockk<ChatModel>()
        val invalid = "<function_results>leaked tool output</function_results>"
        every { chatModel.call(any<Prompt>()) } returns response(invalid)

        val ex = assertThrows(IllegalStateException::class.java) {
            buildChatClient(chatModel).prompt()
                .user("Write a script.")
                .advisors(RoleTagValidationAdvisor(allowedRoles, maxRetries = 2))
                .call()
                .content()
        }

        assertTrue(ex.message!!.contains("function_results"))
        verify(exactly = 3) { chatModel.call(any<Prompt>()) } // 1 initial + 2 retries
    }
}
