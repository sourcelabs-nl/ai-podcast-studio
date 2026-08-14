package com.aisummarypodcast.tts

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.podcast.PodcastService
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.PodcastStyle
import com.aisummarypodcast.store.TtsProviderType
import com.aisummarypodcast.store.User
import com.aisummarypodcast.user.UserService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.coEvery
import io.mockk.every
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.nio.file.Files
import java.nio.file.Path

@WebMvcTest(PreviewAudioController::class)
class PreviewAudioControllerTest {

    @TempDir
    lateinit var tempDir: Path

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var podcastService: PodcastService

    @MockkBean
    private lateinit var userService: UserService

    @MockkBean
    private lateinit var previewAudioService: PreviewAudioService

    /** WebConfig, picked up by the slice, needs it; this controller does not. */
    @MockkBean(relaxed = true)
    private lateinit var appProperties: AppProperties

    private val userId = "user-1"
    private val podcastId = "podcast-1"
    private val user = User(id = userId, name = "Test User")
    private val podcast = Podcast(
        id = podcastId,
        userId = userId,
        name = "Test",
        topic = "tech",
        ttsProvider = TtsProviderType.INWORLD,
        style = PodcastStyle.NEWS_BRIEFING
    )

    private val body = """{"scriptText":"Hello there."}"""

    @BeforeEach
    fun setUp() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
    }

    @Test
    fun `sample returns mpeg audio bytes`() {
        coEvery { previewAudioService.synthesizeSample(podcast, "Hello there.") } returns byteArrayOf(1, 2, 3)

        val result = mockMvc.perform(post("/users/$userId/podcasts/$podcastId/preview/audio/sample").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn()

        mockMvc.perform(asyncDispatch(result))
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Type", "audio/mpeg"))
    }

    @Test
    fun `sample rejects a blank script`() {
        val result = mockMvc.perform(
            post("/users/$userId/podcasts/$podcastId/preview/audio/sample")
                .contentType(MediaType.APPLICATION_JSON).content("""{"scriptText":"  "}""")
        ).andReturn()

        mockMvc.perform(asyncDispatch(result)).andExpect(status().isBadRequest)
    }

    @Test
    fun `sample is not found for a podcast the user does not own`() {
        every { podcastService.findById(podcastId) } returns podcast.copy(userId = "someone-else")

        val result = mockMvc.perform(post("/users/$userId/podcasts/$podcastId/preview/audio/sample").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn()

        mockMvc.perform(asyncDispatch(result)).andExpect(status().isNotFound)
    }

    @Test
    fun `estimate returns the character count and cost`() {
        every { previewAudioService.estimate(podcast, "Hello there.") } returns PreviewAudioEstimate(12, 7)

        mockMvc.perform(post("/users/$userId/podcasts/$podcastId/preview/audio/estimate").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.characters").value(12))
            .andExpect(jsonPath("$.costCents").value(7))
    }

    @Test
    fun `estimate is not found for an unknown user`() {
        every { userService.findById(userId) } returns null

        mockMvc.perform(post("/users/$userId/podcasts/$podcastId/preview/audio/estimate").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `stored audio is streamed as mpeg`() {
        val file = tempDir.resolve("preview.mp3")
        Files.write(file, byteArrayOf(1, 2, 3))
        every { previewAudioService.findStoredAudio(podcastId, "audio-1") } returns file

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/preview/audio/audio-1"))
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Type", "audio/mpeg"))
    }

    @Test
    fun `stored audio is not found when the store does not own it`() {
        every { previewAudioService.findStoredAudio(podcastId, "audio-1") } returns null

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/preview/audio/audio-1"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `stored audio of another user's podcast is not reachable`() {
        every { podcastService.findById(podcastId) } returns podcast.copy(userId = "someone-else")

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/preview/audio/audio-1"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `full audio generation rejects a blank script`() {
        mockMvc.perform(
            post("/users/$userId/podcasts/$podcastId/preview/audio")
                .contentType(MediaType.APPLICATION_JSON).content("""{"scriptText":""}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `full audio generation is not found for a podcast the user does not own`() {
        every { podcastService.findById(podcastId) } returns podcast.copy(userId = "someone-else")

        mockMvc.perform(post("/users/$userId/podcasts/$podcastId/preview/audio").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isNotFound)
    }
}
