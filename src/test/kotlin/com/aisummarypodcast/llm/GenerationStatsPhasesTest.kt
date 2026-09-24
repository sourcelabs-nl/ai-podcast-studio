package com.aisummarypodcast.llm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GenerationStatsPhasesTest {

    // Experiment episode 276's compose request (2026-09-24), a 160,582 ms request served by Venice.
    private val compose = GenerationStats(
        firstContentMs = 130_538, generationTimeMs = 159_954, nativeCompletionTokens = 14_020,
        nativeReasoningTokens = 10_637, finishReason = "stop", servedProvider = "Venice",
        attempts = listOf(ProviderAttempt("Venice", 200, 1_777))
    )

    @Test
    fun `splits a request into startup, reasoning and writing`() {
        val phases = compose.phases()

        assertEquals(1_777L, phases.startupMs)
        assertEquals(128_761L, phases.reasoningMs)
        assertEquals(29_416L, phases.writingMs)
        assertEquals(14_020 / 158.177, phases.tokensPerSecond!!, 0.001)
    }

    @Test
    fun `failed attempts before the served one count as startup`() {
        val fellBack = compose.copy(attempts = listOf(ProviderAttempt("DeepInfra", 503, 30_000), ProviderAttempt("Venice", 200, 1_777)))

        assertEquals(31_777L, fellBack.phases().startupMs)
    }

    @Test
    fun `a phase whose inputs are missing is null`() {
        val phases = compose.copy(attempts = emptyList(), firstContentMs = null).phases()

        assertNull(phases.startupMs)
        assertNull(phases.reasoningMs)
        assertNull(phases.writingMs)
        assertNull(phases.tokensPerSecond)
    }
}
