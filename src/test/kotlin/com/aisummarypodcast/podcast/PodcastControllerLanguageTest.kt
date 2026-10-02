package com.aisummarypodcast.podcast

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.llm.LlmPipeline
import com.aisummarypodcast.source.SourceAggregator
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.User
import com.aisummarypodcast.user.UserService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(PodcastController::class)
@Import(PodcastValidator::class, PodcastAccess::class)
class PodcastControllerLanguageTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var podcastService: PodcastService

    @MockkBean
    private lateinit var userService: UserService

    @MockkBean
    private lateinit var llmPipeline: LlmPipeline

    @MockkBean(relaxed = true)
    private lateinit var episodeService: EpisodeService

    @MockkBean(relaxed = true)
    private lateinit var previewStreamer: PreviewStreamer

    @MockkBean(relaxed = true)
    private lateinit var appProperties: AppProperties

    @MockkBean(relaxed = true)
    private lateinit var sourceRepository: com.aisummarypodcast.store.SourceRepository

    @MockkBean(relaxed = true)
    private lateinit var articleRepository: com.aisummarypodcast.store.ArticleRepository

    @MockkBean(relaxed = true)
    private lateinit var sourceAggregator: SourceAggregator

    private val userId = "user-1"
    private val user = User(id = userId, name = "Test User")
    private val podcastId = "podcast-1"

    @Test
    fun `create podcast with valid language`() {
        every { userService.findById(userId) } returns user
        every { podcastService.create(userId, "My Podcast", "tech", any()) } returns Podcast(
            id = podcastId, userId = userId, name = "My Podcast", topic = "tech", language = "nl"
        )

        mockMvc.perform(
            post("/users/$userId/podcasts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"My Podcast","topic":"tech","language":"nl"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.language").value("nl"))
    }

    @Test
    fun `create podcast with invalid language returns 400`() {
        every { userService.findById(userId) } returns user

        mockMvc.perform(
            post("/users/$userId/podcasts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"My Podcast","topic":"tech","language":"xx"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Unsupported language: xx"))
    }

    @Test
    fun `create podcast without language defaults to en`() {
        every { userService.findById(userId) } returns user
        every { podcastService.create(userId, "My Podcast", "tech", any()) } returns Podcast(
            id = podcastId, userId = userId, name = "My Podcast", topic = "tech", language = "en"
        )

        mockMvc.perform(
            post("/users/$userId/podcasts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"My Podcast","topic":"tech"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.language").value("en"))
    }

    @Test
    fun `update podcast with valid language`() {
        val existing = Podcast(id = podcastId, userId = userId, name = "Old", topic = "tech", language = "en")
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns existing
        every { podcastService.update(podcastId, any()) } returns existing.copy(name = "Old", language = "fr")

        mockMvc.perform(
            put("/users/$userId/podcasts/$podcastId")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Old","topic":"tech","language":"fr"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.language").value("fr"))
    }

    @Test
    fun `update podcast with invalid language returns 400`() {
        val existing = Podcast(id = podcastId, userId = userId, name = "Old", topic = "tech", language = "en")
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns existing

        mockMvc.perform(
            put("/users/$userId/podcasts/$podcastId")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Old","topic":"tech","language":"zz"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Unsupported language: zz"))
    }

    @Test
    fun `get podcast includes language in response`() {
        val podcast = Podcast(id = podcastId, userId = userId, name = "My Podcast", topic = "tech", language = "de")
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.language").value("de"))
    }
}
