package com.aisummarypodcast.research

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.store.User
import com.aisummarypodcast.user.UserService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ResearchTestController::class)
class ResearchTestControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @MockkBean private lateinit var userService: UserService
    @MockkBean private lateinit var researchService: ResearchService

    // WebConfig sits in the sliced context and needs it; the controller itself does not use it.
    @MockkBean(relaxed = true) private lateinit var appProperties: AppProperties

    @Test
    fun `returns the result of the Tavily check`() {
        every { userService.findById("u1") } returns User(id = "u1", name = "Test")
        every { researchService.testTavily("u1") } returns ResearchTestResult(true, "Tavily reachable. Returned 1 result(s).", 1)

        mockMvc.perform(post("/users/u1/research/test/tavily"))
            .andExpect(status().isOk)
            .andExpect(content().json("""{"success":true,"message":"Tavily reachable. Returned 1 result(s).","resultCount":1}"""))
    }

    @Test
    fun `answers 404 for an unknown user`() {
        every { userService.findById("u1") } returns null

        mockMvc.perform(post("/users/u1/research/test/tavily")).andExpect(status().isNotFound)
    }
}
