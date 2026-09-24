package com.aisummarypodcast.llm

import tools.jackson.databind.json.JsonMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TopicOrderExtractorTest {

    private val extractor = TopicOrderExtractor(JsonMapper.builder().build())


    @Test
    fun `extracts topic order from valid response`() {
        val response = """
            Welcome to the podcast. Today we discuss AI safety and new releases.

            |||TOPIC_ORDER|||
            ["AI Safety", "New Model Releases", "Code Quality"]
            |||END_TOPIC_ORDER|||
        """.trimIndent()

        val result = extractor.extract(response)

        assertEquals(listOf("AI Safety", "New Model Releases", "Code Quality"), result.topicOrder)
        assertEquals("Welcome to the podcast. Today we discuss AI safety and new releases.", result.script)
    }

    @Test
    fun `returns empty topic order when no delimiter present`() {
        val response = "Welcome to the podcast. No topic order here."

        val result = extractor.extract(response)

        assertEquals(emptyList<String>(), result.topicOrder)
        assertEquals(response, result.script)
    }

    @Test
    fun `returns empty topic order when end delimiter missing`() {
        val response = """
            Script text here.

            |||TOPIC_ORDER|||
            ["Topic A", "Topic B"]
        """.trimIndent()

        val result = extractor.extract(response)

        assertEquals(emptyList<String>(), result.topicOrder)
        assertEquals(response, result.script)
    }

    @Test
    fun `returns empty topic order when JSON is malformed`() {
        val response = """
            Script text here.

            |||TOPIC_ORDER|||
            not valid json
            |||END_TOPIC_ORDER|||
        """.trimIndent()

        val result = extractor.extract(response)

        assertEquals(emptyList<String>(), result.topicOrder)
        assertEquals("Script text here.", result.script)
    }

    @Test
    fun `strips trailing whitespace from script`() {
        val response = "Script text here.   \n\n|||TOPIC_ORDER|||\n[\"A\"]\n|||END_TOPIC_ORDER|||"

        val result = extractor.extract(response)

        assertEquals(listOf("A"), result.topicOrder)
        assertEquals("Script text here.", result.script)
    }

    @Test
    fun `handles content after end delimiter`() {
        val response = "Script.\n|||TOPIC_ORDER|||\n[\"X\"]\n|||END_TOPIC_ORDER|||\nSome trailing text"

        val result = extractor.extract(response)

        assertEquals(listOf("X"), result.topicOrder)
        assertEquals("Script.", result.script)
    }
}
