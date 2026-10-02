package com.aisummarypodcast.podcast

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class PodcastExceptionHandlerTest {

    private val handler = PodcastExceptionHandler()

    @Test
    fun `a status conflict maps to 409 with only the error message`() {
        val response = handler.handleStatusConflict(EpisodeStatusNotAllowedException("Episode is not in FAILED status"))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(mapOf("error" to "Episode is not in FAILED status"), response.body)
    }

    @Test
    fun `an invalid podcast maps to 400 with only the error message`() {
        val response = handler.handleInvalidPodcast(InvalidPodcastException("Unsupported language: xx"))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(mapOf("error" to "Unsupported language: xx"), response.body)
    }

    @Test
    fun `invalid compose settings map to 422 with only the error message`() {
        val response = handler.handleInvalidComposeSettings(
            InvalidComposeSettingsException("composeSettings.temperature must be a number")
        )

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
        assertEquals(mapOf("error" to "composeSettings.temperature must be a number"), response.body)
    }
}
