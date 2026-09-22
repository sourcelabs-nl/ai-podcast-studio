package com.aisummarypodcast.podcast

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodeMatchDetails
import com.aisummarypodcast.store.EpisodeStatus
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.User
import com.aisummarypodcast.user.UserService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType

import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(EpisodeController::class)
class EpisodeControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var podcastService: PodcastService

    @MockkBean
    private lateinit var userService: UserService

    @MockkBean(relaxed = true)
    private lateinit var episodeService: EpisodeService

    @MockkBean(relaxed = true)
    private lateinit var episodeSearchService: EpisodeSearchService

    @MockkBean(relaxed = true)
    private lateinit var appProperties: AppProperties

    private val userId = "user-1"
    private val user = User(id = userId, name = "Test User")
    private val podcastId = "podcast-1"
    private val podcast = Podcast(id = podcastId, userId = userId, name = "Test", topic = "tech")

    private val pendingEpisode = Episode(
        id = 1L, podcastId = podcastId, generatedAt = "2025-01-01T00:00:00Z",
        scriptText = "Test script", status = EpisodeStatus.PENDING_REVIEW
    )

    private val generatedEpisode = Episode(
        id = 2L, podcastId = podcastId, generatedAt = "2025-01-01T00:00:00Z",
        scriptText = "Test script", status = EpisodeStatus.GENERATED,
        audioFilePath = "/audio/test.mp3", durationSeconds = 120
    )

    @Test
    fun `list episodes returns paged envelope with all episodes`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findByPodcastIdPaged(podcastId, emptyList(), any()) } returns
            org.springframework.data.domain.PageImpl(listOf(pendingEpisode, generatedEpisode))

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/episodes"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.page").value(0))
    }

    @Test
    fun `list episodes with a search query delegates to the search service`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeSearchService.search(podcastId, emptyList(), "retriever", any()) } returns
            org.springframework.data.domain.PageImpl(
                listOf(EpisodeSearchHit(generatedEpisode, EpisodeMatchDetails(listOf("Retriever config"), listOf("A headline"))))
            )

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/episodes?q=retriever"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].matches.topics[0]").value("Retriever config"))
            .andExpect(jsonPath("$.items[0].matches.articleTitles[0]").value("A headline"))
            .andExpect(jsonPath("$.items[0].matches.scriptOnly").value(false))
            .andExpect(jsonPath("$.items[0].matches.topicTotal").value(1))
            .andExpect(jsonPath("$.items[0].matches.articleTotal").value(1))

        verify(exactly = 0) { episodeService.findByPodcastIdPaged(any(), any(), any()) }
    }

    @Test
    fun `search combines with the status filter`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every {
            episodeSearchService.search(podcastId, listOf(EpisodeStatus.GENERATED), "retriever", any())
        } returns org.springframework.data.domain.PageImpl(
            listOf(EpisodeSearchHit(generatedEpisode, EpisodeMatchDetails(emptyList(), emptyList())))
        )

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/episodes?q=retriever&status=GENERATED"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].matches.scriptOnly").value(true))
    }

    @Test
    fun `a blank query falls through to the plain listing`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findByPodcastIdPaged(podcastId, emptyList(), any()) } returns
            org.springframework.data.domain.PageImpl(listOf(pendingEpisode))

        // Passed via param() rather than in the URL: MockMvc does not percent-decode the query
        // string, so "?q=%20" would arrive as the literal three-character value.
        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/episodes").param("q", "   "))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].matches").doesNotExist())

        verify(exactly = 0) { episodeSearchService.search(any(), any(), any(), any()) }
    }

    @Test
    fun `a single-character query falls through to the plain listing`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findByPodcastIdPaged(podcastId, emptyList(), any()) } returns
            org.springframework.data.domain.PageImpl(listOf(pendingEpisode))

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/episodes?q=a"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].matches").doesNotExist())

        verify(exactly = 0) { episodeSearchService.search(any(), any(), any(), any()) }
    }

    @Test
    fun `match lists are capped while the total counts every match`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        val topics = (1..9).map { "Topic $it" }
        every { episodeSearchService.search(podcastId, emptyList(), "topic", any()) } returns
            org.springframework.data.domain.PageImpl(
                listOf(EpisodeSearchHit(generatedEpisode, EpisodeMatchDetails(topics, emptyList(), topicTotal = topics.size)))
            )

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/episodes?q=topic"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].matches.topics.length()").value(EpisodeSearchService.MAX_MATCHES_PER_EPISODE))
            .andExpect(jsonPath("$.items[0].matches.topicTotal").value(9))
    }

    @Test
    fun `list episodes with status filter`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every {
            episodeService.findByPodcastIdPaged(podcastId, listOf(EpisodeStatus.PENDING_REVIEW), any())
        } returns org.springframework.data.domain.PageImpl(listOf(pendingEpisode))

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/episodes?status=PENDING_REVIEW"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].status").value("PENDING_REVIEW"))
    }

    @Test
    fun `list episodes accepts multiple status values`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every {
            episodeService.findByPodcastIdPaged(
                podcastId,
                listOf(EpisodeStatus.PENDING_REVIEW, EpisodeStatus.GENERATED),
                any()
            )
        } returns org.springframework.data.domain.PageImpl(listOf(pendingEpisode, generatedEpisode))

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/episodes?status=PENDING_REVIEW&status=GENERATED"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(2))
    }

    @Test
    fun `list episodes rejects negative page`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/episodes?page=-1"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `list episodes rejects pageSize above max`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/episodes?pageSize=500"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `list episodes for non-existing podcast returns 404`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns null

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/episodes"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `get single episode`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(1L) } returns pendingEpisode

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/episodes/1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.scriptText").value("Test script"))
            .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
    }

    @Test
    fun `get non-existing episode returns 404`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(99L) } returns null

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/episodes/99"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `edit script of pending episode`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(1L) } returns pendingEpisode
        every { episodeService.updateScript(pendingEpisode, "Updated script") } returns pendingEpisode.copy(scriptText = "Updated script")

        mockMvc.perform(
            put("/users/$userId/podcasts/$podcastId/episodes/1/script")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"scriptText":"Updated script"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.scriptText").value("Updated script"))
    }

    @Test
    fun `edit script of non-pending episode returns 409`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(2L) } returns generatedEpisode

        mockMvc.perform(
            put("/users/$userId/podcasts/$podcastId/episodes/2/script")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"scriptText":"Updated script"}""")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").exists())
    }

    @Test
    fun `approve pending episode returns 202`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(1L) } returns pendingEpisode
        justRun { episodeService.approveAndGenerateAudio(pendingEpisode, podcast) }

        mockMvc.perform(post("/users/$userId/podcasts/$podcastId/episodes/1/approve"))
            .andExpect(status().isAccepted)

        verify { episodeService.approveAndGenerateAudio(pendingEpisode, podcast) }
    }

    @Test
    fun `approve failed episode returns 202`() {
        val failedEpisode = pendingEpisode.copy(status = EpisodeStatus.FAILED)
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(1L) } returns failedEpisode
        justRun { episodeService.approveAndGenerateAudio(failedEpisode, podcast) }

        mockMvc.perform(post("/users/$userId/podcasts/$podcastId/episodes/1/approve"))
            .andExpect(status().isAccepted)
    }

    @Test
    fun `approve generated episode returns 409`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(2L) } returns generatedEpisode

        mockMvc.perform(post("/users/$userId/podcasts/$podcastId/episodes/2/approve"))
            .andExpect(status().isConflict)
    }

    @Test
    fun `approve publication of generated episode returns 200`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(2L) } returns generatedEpisode
        every { episodeService.approveForPublication(generatedEpisode, podcast) } returns generatedEpisode.copy(publishApproved = true)

        mockMvc.perform(post("/users/$userId/podcasts/$podcastId/episodes/2/approve-publication"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.publishApproved").value(true))

        verify { episodeService.approveForPublication(generatedEpisode, podcast) }
    }

    @Test
    fun `approve publication of pending episode returns 409`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(1L) } returns pendingEpisode

        mockMvc.perform(post("/users/$userId/podcasts/$podcastId/episodes/1/approve-publication"))
            .andExpect(status().isConflict)
    }

    @Test
    fun `discard pending episode delegates to service`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(1L) } returns pendingEpisode
        justRun { episodeService.discardAndResetArticles(pendingEpisode, podcastId) }

        mockMvc.perform(post("/users/$userId/podcasts/$podcastId/episodes/1/discard"))
            .andExpect(status().isOk)

        verify { episodeService.discardAndResetArticles(pendingEpisode, podcastId) }
    }

    @Test
    fun `discard failed episode calls discardOnly`() {
        val failedEpisode = Episode(
            id = 5L, podcastId = podcastId, generatedAt = "2025-01-01T00:00:00Z",
            scriptText = "", status = EpisodeStatus.FAILED, errorMessage = "TTS failure"
        )
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(5L) } returns failedEpisode
        justRun { episodeService.discardOnly(failedEpisode, podcastId) }

        mockMvc.perform(post("/users/$userId/podcasts/$podcastId/episodes/5/discard"))
            .andExpect(status().isOk)

        verify { episodeService.discardOnly(failedEpisode, podcastId) }
        verify(exactly = 0) { episodeService.discardAndResetArticles(any(), any()) }
    }

    @Test
    fun `discard non-discardable episode returns 409`() {
        val approvedEpisode = Episode(
            id = 3L, podcastId = podcastId, generatedAt = "2025-01-01T00:00:00Z",
            scriptText = "Test script", status = EpisodeStatus.APPROVED
        )
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(3L) } returns approvedEpisode

        mockMvc.perform(post("/users/$userId/podcasts/$podcastId/episodes/3/discard"))
            .andExpect(status().isConflict)
    }

    @Test
    fun `discard GENERATING_AUDIO episode returns 409 with audio message`() {
        val generatingAudioEpisode = Episode(
            id = 4L, podcastId = podcastId, generatedAt = "2025-01-01T00:00:00Z",
            scriptText = "Test script", status = EpisodeStatus.GENERATING_AUDIO
        )
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(4L) } returns generatingAudioEpisode

        mockMvc.perform(post("/users/$userId/podcasts/$podcastId/episodes/4/discard"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("Audio generation is in progress"))
    }

    // --- regenerate-recap tests ---

    @Test
    fun `regenerate-recap returns updated episode`() {
        val episodeWithRecap = generatedEpisode.copy(recap = "New recap.", showNotes = "New recap.")
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(2L) } returns generatedEpisode
        coEvery { episodeService.regenerateRecap(generatedEpisode, podcast) } returns episodeWithRecap

        val mvcResult = mockMvc.perform(post("/users/$userId/podcasts/$podcastId/episodes/2/regenerate-recap"))
            .andReturn()
        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.recap").value("New recap."))
            .andExpect(jsonPath("$.showNotes").value("New recap."))

        coVerify { episodeService.regenerateRecap(generatedEpisode, podcast) }
    }

    @Test
    fun `regenerate-recap for non-existing episode returns 404`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(99L) } returns null

        val mvcResult = mockMvc.perform(post("/users/$userId/podcasts/$podcastId/episodes/99/regenerate-recap"))
            .andReturn()
        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `regenerate-recap for wrong podcast returns 404`() {
        val otherPodcastEpisode = generatedEpisode.copy(podcastId = "other-podcast")
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(2L) } returns otherPodcastEpisode

        val mvcResult = mockMvc.perform(post("/users/$userId/podcasts/$podcastId/episodes/2/regenerate-recap"))
            .andReturn()
        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `regenerate-recap returns 500 on failure`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(2L) } returns generatedEpisode
        coEvery { episodeService.regenerateRecap(generatedEpisode, podcast) } throws RuntimeException("LLM error")

        val mvcResult = mockMvc.perform(post("/users/$userId/podcasts/$podcastId/episodes/2/regenerate-recap"))
            .andReturn()
        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.error").exists())
    }

    @Test
    fun `articles for non-existing episode returns 404`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(99L) } returns null

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/episodes/99/articles"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `articles for episode in wrong podcast returns 404`() {
        val otherPodcastEpisode = pendingEpisode.copy(podcastId = "other-podcast")
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(1L) } returns otherPodcastEpisode

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/episodes/1/articles"))
            .andExpect(status().isNotFound)
    }

    // --- Re-run a past window ---------------------------------------------------------------------

    private val discardedEpisode = Episode(
        id = 3L, podcastId = podcastId, generatedAt = "2025-01-01T00:00:00Z",
        windowStart = "2024-12-31T14:00:00Z", windowEnd = "2025-01-01T14:00:00Z",
        scriptText = "Test script", status = EpisodeStatus.DISCARDED
    )

    @Test
    fun `rerun returns the new episode`() {
        val rerun = discardedEpisode.copy(id = 4L, status = EpisodeStatus.GENERATING)
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(3L) } returns discardedEpisode
        every { podcastService.rerunEpisodeAsync(discardedEpisode, podcast) } returns rerun

        mockMvc.perform(post("/users/$userId/podcasts/$podcastId/episodes/3/rerun"))
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.id").value(4))
            .andExpect(jsonPath("$.windowStart").value("2024-12-31T14:00:00Z"))
            .andExpect(jsonPath("$.windowEnd").value("2025-01-01T14:00:00Z"))
    }

    @Test
    fun `rerun of an episode that cannot be re-run returns 409`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(2L) } returns generatedEpisode
        every { podcastService.rerunEpisodeAsync(generatedEpisode, podcast) } throws
            EpisodeNotRerunnableException("Episode 2 is GENERATED; discard this one first")

        mockMvc.perform(post("/users/$userId/podcasts/$podcastId/episodes/2/rerun"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("episode_not_rerunnable"))
    }

    @Test
    fun `rerun of a non-existing episode returns 404`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(99L) } returns null

        mockMvc.perform(post("/users/$userId/podcasts/$podcastId/episodes/99/rerun"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `rerun of an episode in another podcast returns 404`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(3L) } returns discardedEpisode.copy(podcastId = "other-podcast")

        mockMvc.perform(post("/users/$userId/podcasts/$podcastId/episodes/3/rerun"))
            .andExpect(status().isNotFound)
    }

    private val focusEpisode = pendingEpisode.copy(id = 3L, focus = "Claude Opus 5.5 release", reviewFeedback = "shorter")

    private fun stubFocusEpisode() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(3L) } returns focusEpisode
    }

    @Test
    fun `get episode exposes the focus and the latest review feedback`() {
        stubFocusEpisode()

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/episodes/3"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.focus").value("Claude Opus 5.5 release"))
            .andExpect(jsonPath("$.reviewFeedback").value("shorter"))
    }

    @Test
    fun `research sources of an episode are listed`() {
        stubFocusEpisode()
        every { episodeService.findResearchSources(3L) } returns listOf(
            com.aisummarypodcast.store.EpisodeResearchSource(episodeId = 3L, query = "opus benchmarks", title = "Bench", url = "https://b", ordinal = 0)
        )

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId/episodes/3/research-sources"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].query").value("opus benchmarks"))
            .andExpect(jsonPath("$[0].url").value("https://b"))
    }

    @Test
    fun `regenerate-script recomposes the focus episode with the feedback`() {
        stubFocusEpisode()
        every { podcastService.recomposeFocusEpisodeAsync(focusEpisode, podcast, "more benchmarks") } returns
            focusEpisode.copy(pipelineStage = "composing")

        mockMvc.perform(
            post("/users/$userId/podcasts/$podcastId/episodes/3/regenerate-script")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"feedback": " more benchmarks "}""")
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.id").value(3))

        verify { podcastService.recomposeFocusEpisodeAsync(focusEpisode, podcast, "more benchmarks") }
    }

    @Test
    fun `regenerate-script is a conflict for an episode that is not a focus episode in review`() {
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcast
        every { episodeService.findById(1L) } returns pendingEpisode
        every { podcastService.recomposeFocusEpisodeAsync(pendingEpisode, podcast, any()) } throws
            EpisodeNotRecomposableException("Episode 1 is not a focus episode awaiting review")

        mockMvc.perform(
            post("/users/$userId/podcasts/$podcastId/episodes/1/regenerate-script")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"feedback": "shorter"}""")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("episode_not_recomposable"))
    }

    @Test
    fun `regenerate-script rejects blank feedback`() {
        stubFocusEpisode()

        mockMvc.perform(
            post("/users/$userId/podcasts/$podcastId/episodes/3/regenerate-script")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"feedback": "  "}""")
        )
            .andExpect(status().isBadRequest)
    }
}
