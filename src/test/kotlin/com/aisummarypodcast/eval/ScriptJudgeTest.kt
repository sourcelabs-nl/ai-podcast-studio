package com.aisummarypodcast.eval

import com.aisummarypodcast.llm.ChatClientFactory
import io.github.resilience4j.retry.RetryRegistry
import io.mockk.mockk
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScriptJudgeTest {

    private val judge = ScriptJudge(
        mockk<ChatClientFactory>(), JsonMapper.builder().build(), RetryRegistry.ofDefaults()
    )

    @Test
    fun `the prompt asks for positions and not for a rating`() {
        val prompt = judge.buildPrompt(listOf("[0] interviewer: Hello.", "[1] expert: Hi."))

        assertTrue(prompt.contains("Report positions only"))
        assertTrue(prompt.contains("do not rate the script"))
        assertTrue(prompt.contains("[0] interviewer: Hello."))
    }

    @Test
    fun `the prompt says an absent device is a finding`() {
        val prompt = judge.buildPrompt(listOf("[0] expert: Only turn."))

        // Without this a model hands back the nearest thing it can find, and an episode with no
        // cliffhanger scores as though it had one.
        assertTrue(prompt.contains("empty list"))
    }

    @Test
    fun `an anchor pointing past the end of the script is dropped`() {
        val anchors = ScriptJudgeAnchors(
            promises = listOf(PromiseAnchor(1, 5), PromiseAnchor(40, 60)),
            humorBeats = listOf(HumorAnchor(2, "expert", true), HumorAnchor(99, "expert", true))
        )

        val clean = judge.withinRange(anchors, turnCount = 10)

        // A turn number the script does not have cannot be checked by opening the script there,
        // which is the one property the anchors exist for.
        assertTrue(clean.promises.size == 1)
        assertTrue(clean.promises.single().promiseTurn == 1)
        assertTrue(clean.humorBeats.size == 1)
    }

    @Test
    fun `a payoff outside the script becomes an unpaid promise, not a dropped one`() {
        val anchors = ScriptJudgeAnchors(promises = listOf(PromiseAnchor(3, 99)))

        val clean = judge.withinRange(anchors, turnCount = 10)

        // The promise is on the page even though the payoff the model claimed is not, and an unpaid
        // promise is exactly the defect the cliffhanger rule exists to catch.
        assertTrue(clean.promises.size == 1)
        assertTrue(clean.promises.single().payoffTurn == null)
    }

    @Test
    fun `the first attempt sends the prompt unchanged`() {
        val prompt = "the prompt"

        assertTrue(judge.promptForAttempt(prompt, 1) == prompt)
    }

    @Test
    fun `a retry never sends the identical prompt`() {
        val prompt = "the prompt"

        val second = judge.promptForAttempt(prompt, 2)
        val third = judge.promptForAttempt(prompt, 3)

        // CachingChatModel keys on prompt text, so an identical retry would replay the unparseable
        // answer from cache without ever reaching the model.
        assertFalse(second == prompt)
        assertFalse(second == third)
        assertTrue(second.startsWith(prompt))
    }
}
