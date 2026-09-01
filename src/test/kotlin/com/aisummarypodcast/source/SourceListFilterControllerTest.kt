package com.aisummarypodcast.source

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.podcast.PodcastService
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.Source
import com.aisummarypodcast.store.SourceType
import com.aisummarypodcast.store.User
import com.aisummarypodcast.user.UserService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * The Sources tab opens on the sources that actually run, and the narrowing has to happen in the
 * backend rather than in the browser: a podcast keeps every source it ever retired.
 */
@WebMvcTest(SourceController::class)
class SourceListFilterControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @MockkBean private lateinit var sourceService: SourceService
    @MockkBean private lateinit var podcastService: PodcastService
    @MockkBean private lateinit var userService: UserService

    // WebConfig sits in the sliced context and needs it; the controller itself does not use it.
    @MockkBean(relaxed = true) private lateinit var appProperties: AppProperties

    private val base = "/users/u1/podcasts/p1/sources"

    private fun source(id: String, enabled: Boolean) = Source(
        id = id, podcastId = "p1", type = SourceType.RSS,
        url = "https://example.com/$id", enabled = enabled, label = id
    )

    private val live = source("s1", true)
    private val retired = source("s2", false)

    @BeforeEach
    fun setUp() {
        every { userService.findById("u1") } returns User(id = "u1", name = "Test")
        every { podcastService.findById("p1") } returns
            Podcast(id = "p1", userId = "u1", name = "Test", topic = "tech")
        every { sourceService.getArticleCounts(any(), any()) } returns emptyMap()
        every { sourceService.getPostCounts(any()) } returns emptyMap()
        every { sourceService.getHostBreakerStates(any()) } returns emptyMap()
    }

    @Test
    fun `omitting the parameter returns every source`() {
        every { sourceService.findByPodcastId("p1", null) } returns listOf(live, retired)

        mockMvc.perform(get(base))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))

        verify { sourceService.findByPodcastId("p1", null) }
    }

    @Test
    fun `enabled true returns only the sources that run`() {
        every { sourceService.findByPodcastId("p1", true) } returns listOf(live)

        mockMvc.perform(get("$base?enabled=true"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value("s1"))
            .andExpect(jsonPath("$[0].enabled").value(true))

        verify { sourceService.findByPodcastId("p1", true) }
    }

    @Test
    fun `enabled false returns only the retired sources`() {
        every { sourceService.findByPodcastId("p1", false) } returns listOf(retired)

        mockMvc.perform(get("$base?enabled=false"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value("s2"))
            .andExpect(jsonPath("$[0].enabled").value(false))
    }

    @Test
    fun `unknown podcast is still a 404 with the filter present`() {
        every { podcastService.findById("p1") } returns null

        mockMvc.perform(get("$base?enabled=true")).andExpect(status().isNotFound)
    }
}
