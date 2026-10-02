package com.aisummarypodcast.api

import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

class ApiExceptionHandlerTest {

    @RestController
    class ThrowingController {
        @GetMapping("/invalid")
        fun invalid(): String = throw InvalidRequestException("bad request text")

        @GetMapping("/unprocessable")
        fun unprocessable(): String = throw UnprocessableRequestException("unprocessable text")

        @GetMapping("/upstream")
        fun upstream(): String = throw UpstreamServiceException("upstream text", RuntimeException("cause"))

        @GetMapping("/missing")
        fun missing(): String = throw ResourceNotFoundException()

        @GetMapping("/failed")
        fun failed(): String = throw OperationFailedException("failed text", RuntimeException("cause"))
    }

    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(ThrowingController())
        .setControllerAdvice(ApiExceptionHandler())
        .build()

    @Test
    fun `an invalid request maps to 400 with the message`() {
        mockMvc.perform(get("/invalid"))
            .andExpect(status().isBadRequest)
            .andExpect(content().json("""{"error":"bad request text"}""", true))
    }

    @Test
    fun `an unprocessable request maps to 422 with the message`() {
        mockMvc.perform(get("/unprocessable"))
            .andExpect(status().isUnprocessableEntity)
            .andExpect(content().json("""{"error":"unprocessable text"}""", true))
    }

    @Test
    fun `an upstream failure maps to 502 with the message`() {
        mockMvc.perform(get("/upstream"))
            .andExpect(status().isBadGateway)
            .andExpect(content().json("""{"error":"upstream text"}""", true))
    }

    @Test
    fun `a failed operation maps to 500 with the message`() {
        mockMvc.perform(get("/failed"))
            .andExpect(status().isInternalServerError)
            .andExpect(content().json("""{"error":"failed text"}""", true))
    }

    @Test
    fun `a missing resource maps to 404 with an empty body`() {
        mockMvc.perform(get("/missing"))
            .andExpect(status().isNotFound)
            .andExpect(content().string(""))
    }
}
