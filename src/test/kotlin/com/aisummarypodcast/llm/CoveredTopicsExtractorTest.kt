package com.aisummarypodcast.llm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CoveredTopicsExtractorTest {

    @Test
    fun `extracts covered topics and recap from valid response`() {
        val response = """
            NVIDIA shipped a new model and OpenClaw kept growing.

            |||COVERED_TOPICS|||
            ["Nemotron 3 Ultra", "OpenClaw growth"]
            |||END_COVERED_TOPICS|||
        """.trimIndent()

        val result = CoveredTopicsExtractor.extract(response)

        assertEquals(listOf("Nemotron 3 Ultra", "OpenClaw growth"), result.coveredTopics)
        assertEquals("NVIDIA shipped a new model and OpenClaw kept growing.", result.recap)
    }

    @Test
    fun `returns empty covered topics when no delimiter present`() {
        val response = "A plain recap with no metadata block."

        val result = CoveredTopicsExtractor.extract(response)

        assertEquals(emptyList<String>(), result.coveredTopics)
        assertEquals(response, result.recap)
    }

    @Test
    fun `returns empty covered topics when end delimiter missing`() {
        val response = "Recap text.\n\n|||COVERED_TOPICS|||\n[\"Topic A\"]"

        val result = CoveredTopicsExtractor.extract(response)

        assertEquals(emptyList<String>(), result.coveredTopics)
        assertEquals("Recap text.", result.recap)
    }

    @Test
    fun `returns empty covered topics when JSON is malformed`() {
        val response = "Recap text.\n|||COVERED_TOPICS|||\nnot valid json\n|||END_COVERED_TOPICS|||"

        val result = CoveredTopicsExtractor.extract(response)

        assertEquals(emptyList<String>(), result.coveredTopics)
        assertEquals("Recap text.", result.recap)
    }

    @Test
    fun `trims whitespace from recap`() {
        val response = "Recap text.   \n\n|||COVERED_TOPICS|||\n[\"A\"]\n|||END_COVERED_TOPICS|||"

        val result = CoveredTopicsExtractor.extract(response)

        assertEquals(listOf("A"), result.coveredTopics)
        assertEquals("Recap text.", result.recap)
    }
}
