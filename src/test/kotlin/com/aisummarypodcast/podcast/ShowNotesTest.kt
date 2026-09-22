package com.aisummarypodcast.podcast

import com.aisummarypodcast.llm.EpisodeRecapGenerator
import com.aisummarypodcast.llm.ModelResolver
import com.aisummarypodcast.store.ArticleRepository
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodeArticleRepository
import com.aisummarypodcast.store.EpisodeRepository
import com.aisummarypodcast.store.EpisodeStatus
import com.aisummarypodcast.store.PodcastRepository
import com.aisummarypodcast.store.PostArticleRepository
import com.aisummarypodcast.tts.TtsPipeline
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.lang.reflect.Method

class ShowNotesTest {

    private val episodeRepository = mockk<EpisodeRepository>()
    private val podcastRepository = mockk<PodcastRepository>()
    private val ttsPipeline = mockk<TtsPipeline>()
    private val episodeArticleRepository = mockk<EpisodeArticleRepository>()
    private val articleRepository = mockk<ArticleRepository>()
    private val episodeRecapGenerator = mockk<EpisodeRecapGenerator>()
    private val modelResolver = mockk<ModelResolver>()
    private val postArticleRepository = mockk<PostArticleRepository>()
    private val episodeSourcesGenerator = mockk<EpisodeSourcesGenerator>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

    private val articleEligibilityService = mockk<com.aisummarypodcast.llm.ArticleEligibilityService>()

    private val audioGenerationService = mockk<AudioGenerationService>(relaxed = true)

    private val episodeCandidateArticleRepository =
        mockk<com.aisummarypodcast.store.EpisodeCandidateArticleRepository>(relaxed = true)

    private val service = EpisodeService(
        episodeRepository, podcastRepository, ttsPipeline,
        episodeArticleRepository, episodeCandidateArticleRepository, articleRepository,
        episodeRecapGenerator, modelResolver, postArticleRepository,
        episodeSourcesGenerator, articleEligibilityService, eventPublisher,
        audioGenerationService, mockk<com.aisummarypodcast.eval.EvaluationRunRecorder>(relaxed = true),
        mockk<com.aisummarypodcast.store.LlmCallRepository>(relaxed = true),
        mockk<com.aisummarypodcast.config.AppProperties>(relaxed = true)
    )

    private val generateAndStoreShowNotes: Method = EpisodeService::class.java
        .getDeclaredMethod("generateAndStoreShowNotes", Episode::class.java)
        .also { it.isAccessible = true }

    @Test
    fun `show notes stores recap text`() {
        val episode = Episode(
            id = 1L, podcastId = "p1", generatedAt = "2025-01-01T00:00:00Z",
            scriptText = "Script", status = EpisodeStatus.GENERATED,
            recap = "Today's recap summary."
        )
        val saved = episode.copy(showNotes = "Today's recap summary.")
        every { episodeRepository.save(any()) } returns saved

        val result = generateAndStoreShowNotes.invoke(service, episode) as Episode
        assertEquals("Today's recap summary.", result.showNotes)
        verify { episodeRepository.save(match { it.showNotes == "Today's recap summary." }) }
    }

    @Test
    fun `show notes returns episode unchanged when recap is null`() {
        val episode = Episode(
            id = 1L, podcastId = "p1", generatedAt = "2025-01-01T00:00:00Z",
            scriptText = "Script", status = EpisodeStatus.GENERATED,
            recap = null
        )

        val result = generateAndStoreShowNotes.invoke(service, episode) as Episode
        assertEquals(episode, result)
        verify(exactly = 0) { episodeRepository.save(any()) }
    }
}
