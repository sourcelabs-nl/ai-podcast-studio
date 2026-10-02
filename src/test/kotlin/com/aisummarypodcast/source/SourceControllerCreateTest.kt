package com.aisummarypodcast.source

import com.aisummarypodcast.podcast.EpisodeService
import com.aisummarypodcast.podcast.PodcastAccess
import org.springframework.context.annotation.Import
import com.aisummarypodcast.api.UnprocessableRequestException
import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.podcast.PodcastService
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.SourceType
import com.aisummarypodcast.store.User
import com.aisummarypodcast.user.UserService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(SourceController::class)
@Import(PodcastAccess::class)
class SourceControllerCreateTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @MockkBean private lateinit var sourceService: SourceService
    @MockkBean private lateinit var podcastService: PodcastService
    @MockkBean(relaxed = true) private lateinit var episodeService: EpisodeService
    @MockkBean private lateinit var userService: UserService

    // WebConfig sits in the sliced context and needs it; the controller itself does not use it.
    @MockkBean(relaxed = true) private lateinit var appProperties: AppProperties

    @BeforeEach
    fun setUp() {
        every { userService.findById("u1") } returns User(id = "u1", name = "Test")
        every { podcastService.findById("p1") } returns Podcast(id = "p1", userId = "u1", name = "Test", topic = "tech")
    }

    @Test
    fun `create answers 422 with the validation message when the source is not acceptable`() {
        every { sourceService.create("p1", SourceType.RSS, "https://bad.com/feed", any()) } throws
            UnprocessableRequestException("RSS feed at https://bad.com/feed returned no items")

        mockMvc.perform(
            post("/users/u1/podcasts/p1/sources")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"type":"rss","url":"https://bad.com/feed"}""")
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(content().json("""{"error":"RSS feed at https://bad.com/feed returned no items"}""", true))
    }
}
