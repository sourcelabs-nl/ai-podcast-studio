package com.aisummarypodcast.podcast

import com.aisummarypodcast.config.ModelReference
import com.aisummarypodcast.llm.PipelineStage
import com.aisummarypodcast.llm.RunOverrides
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ExperimentMappersTest {

    @Test
    fun `a request maps to a plan with a leading baseline and stage-keyed overrides`() {
        val plan = ExperimentRequest(
            variants = listOf(
                ExperimentVariantRequest(
                    name = "luna",
                    models = mapOf("compose" to ModelReference("openrouter", "openai/gpt-6-luna")),
                    reasoningEffort = mapOf("compose" to "none"),
                    providerSort = "throughput"
                )
            ),
            repeats = 2,
            includeBaseline = true
        ).toPlan()

        assertEquals(4, plan.runCount)
        assertEquals(ExperimentVariant(BASELINE_VARIANT, RunOverrides()), plan.variants[0])
        assertEquals(
            RunOverrides(
                models = mapOf(PipelineStage.COMPOSE to ModelReference("openrouter", "openai/gpt-6-luna")),
                reasoningEffort = mapOf(PipelineStage.COMPOSE to "none"),
                providerSort = "throughput"
            ),
            plan.variants[1].overrides
        )
    }

    @Test
    fun `an unusable request is refused`() {
        val ok = ExperimentVariantRequest(name = "a")
        for (request in listOf(
            ExperimentRequest(),
            ExperimentRequest(listOf(ok), repeats = 0),
            ExperimentRequest(listOf(ok, ok)),
            ExperimentRequest(listOf(ExperimentVariantRequest(name = " "))),
            ExperimentRequest(listOf(ExperimentVariantRequest(name = "a", reasoningEffort = mapOf("writer" to "low")))),
            ExperimentRequest(listOf(ExperimentVariantRequest(name = "a", providerSort = "fastest"))),
            ExperimentRequest(listOf(ExperimentVariantRequest(name = "a", targetWords = 0))),
            ExperimentRequest(listOf(ExperimentVariantRequest(name = BASELINE_VARIANT)), includeBaseline = true)
        )) {
            assertThrows<InvalidExperimentException>(request.toString()) { request.toPlan() }
        }
    }
}
