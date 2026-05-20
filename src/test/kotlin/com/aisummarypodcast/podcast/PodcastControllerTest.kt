package com.aisummarypodcast.podcast

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.source.SourceAggregator
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.PodcastStyle
import com.aisummarypodcast.store.TtsProviderType
import com.aisummarypodcast.store.User
import com.aisummarypodcast.user.UserService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.slot
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(PodcastController::class)
class PodcastControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var podcastService: PodcastService

    @MockkBean
    private lateinit var userService: UserService

    @MockkBean(relaxed = true)
    private lateinit var appProperties: AppProperties

    @MockkBean(relaxed = true)
    private lateinit var sourceRepository: com.aisummarypodcast.store.SourceRepository

    @MockkBean(relaxed = true)
    private lateinit var articleRepository: com.aisummarypodcast.store.ArticleRepository

    @MockkBean(relaxed = true)
    private lateinit var episodeService: EpisodeService

    @MockkBean(relaxed = true)
    private lateinit var sourceAggregator: SourceAggregator

    @MockkBean(relaxed = true)
    private lateinit var pipelineStateTracker: PipelineStateTracker

    private val userId = "user-1"
    private val user = User(id = userId, name = "Test User")
    private val podcastId = "podcast-1"

    @BeforeEach
    fun setupValidation() {
        every { podcastService.validateTtsConfig(any(), any(), any()) } answers {
            val ttsProvider = firstArg<TtsProviderType>()
            val style = secondArg<PodcastStyle>()
            val voices = thirdArg<Map<String, String>?>()
            val dialogueProviders = setOf(TtsProviderType.ELEVENLABS, TtsProviderType.INWORLD)
            when {
                style == PodcastStyle.DIALOGUE && ttsProvider !in dialogueProviders -> "Dialogue style requires ElevenLabs or Inworld as TTS provider"
                style == PodcastStyle.DIALOGUE && (voices == null || voices.size < 2) -> "Dialogue style requires at least two voice roles in ttsVoices (e.g., host and cohost)"
                style == PodcastStyle.INTERVIEW && ttsProvider !in dialogueProviders -> "Interview style requires ElevenLabs or Inworld as TTS provider"
                style == PodcastStyle.INTERVIEW && (voices == null || voices.size < 2) -> "Interview style requires at least two voice roles in ttsVoices (interviewer and expert)"
                style == PodcastStyle.INTERVIEW && voices != null && voices.keys != setOf("interviewer", "expert") -> "Interview style requires exactly 'interviewer' and 'expert' voice roles"
                else -> null
            }
        }
    }

    @Test
    fun `create podcast with relevanceThreshold and requireReview`() {
        val podcastSlot = slot<Podcast>()
        every { userService.findById(userId) } returns user
        every { podcastService.create(userId, "My Podcast", "tech", capture(podcastSlot)) } answers {
            podcastSlot.captured.copy(id = podcastId)
        }

        mockMvc.perform(
            post("/users/$userId/podcasts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"My Podcast","topic":"tech","relevanceThreshold":3,"requireReview":true}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.relevanceThreshold").value(3))
            .andExpect(jsonPath("$.requireReview").value(true))
    }

    @Test
    fun `update podcast with relevanceThreshold and requireReview`() {
        val existing = Podcast(
            id = podcastId, userId = userId, name = "My Podcast", topic = "tech",
            relevanceThreshold = 5, requireReview = false
        )
        val updatedSlot = slot<Podcast>()
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns existing
        every { podcastService.update(podcastId, capture(updatedSlot)) } answers {
            updatedSlot.captured
        }

        mockMvc.perform(
            put("/users/$userId/podcasts/$podcastId")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"My Podcast","topic":"tech","relevanceThreshold":8,"requireReview":true}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.relevanceThreshold").value(8))
            .andExpect(jsonPath("$.requireReview").value(true))
    }

    @Test
    fun `create podcast with all customization fields`() {
        val podcastSlot = slot<Podcast>()
        every { userService.findById(userId) } returns user
        every { podcastService.create(userId, "My Podcast", "tech", capture(podcastSlot)) } answers {
            podcastSlot.captured.copy(id = podcastId)
        }

        mockMvc.perform(
            post("/users/$userId/podcasts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"My Podcast","topic":"tech","ttsProvider":"openai","ttsVoices":{"default":"alloy"},"ttsSettings":{"speed":"1.25"},"style":"casual","targetWords":800,"relevanceThreshold":3,"requireReview":true,"cron":"0 0 8 * * MON-FRI"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.ttsProvider").value("openai"))
            .andExpect(jsonPath("$.ttsVoices.default").value("alloy"))
            .andExpect(jsonPath("$.style").value("casual"))
            .andExpect(jsonPath("$.targetWords").value(800))
            .andExpect(jsonPath("$.relevanceThreshold").value(3))
            .andExpect(jsonPath("$.requireReview").value(true))
            .andExpect(jsonPath("$.cron").value("0 0 8 * * MON-FRI"))
    }

    @Test
    fun `create interview podcast with valid config succeeds`() {
        val podcastSlot = slot<Podcast>()
        every { userService.findById(userId) } returns user
        every { podcastService.create(userId, "My Podcast", "tech", capture(podcastSlot)) } answers {
            podcastSlot.captured.copy(id = podcastId)
        }

        mockMvc.perform(
            post("/users/$userId/podcasts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"My Podcast","topic":"tech","style":"interview","ttsProvider":"elevenlabs","ttsVoices":{"interviewer":"v1","expert":"v2"},"speakerNames":{"interviewer":"Alice","expert":"Bob"}}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.style").value("interview"))
            .andExpect(jsonPath("$.speakerNames.interviewer").value("Alice"))
            .andExpect(jsonPath("$.speakerNames.expert").value("Bob"))
    }

    @Test
    fun `create interview podcast with wrong provider returns 400`() {
        every { userService.findById(userId) } returns user

        mockMvc.perform(
            post("/users/$userId/podcasts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"My Podcast","topic":"tech","style":"interview","ttsProvider":"openai","ttsVoices":{"interviewer":"v1","expert":"v2"}}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Interview style requires ElevenLabs or Inworld as TTS provider"))
    }

    @Test
    fun `create interview podcast with wrong role keys returns 400`() {
        every { userService.findById(userId) } returns user

        mockMvc.perform(
            post("/users/$userId/podcasts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"My Podcast","topic":"tech","style":"interview","ttsProvider":"elevenlabs","ttsVoices":{"host":"v1","cohost":"v2"}}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Interview style requires exactly 'interviewer' and 'expert' voice roles"))
    }

    @Test
    fun `create interview podcast with missing voices returns 400`() {
        every { userService.findById(userId) } returns user

        mockMvc.perform(
            post("/users/$userId/podcasts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"My Podcast","topic":"tech","style":"interview","ttsProvider":"elevenlabs","ttsVoices":{"interviewer":"v1"}}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Interview style requires at least two voice roles in ttsVoices (interviewer and expert)"))
    }

    @Test
    fun `create podcast with sponsor`() {
        val podcastSlot = slot<Podcast>()
        every { userService.findById(userId) } returns user
        every { podcastService.create(userId, "My Podcast", "tech", capture(podcastSlot)) } answers {
            podcastSlot.captured.copy(id = podcastId)
        }

        mockMvc.perform(
            post("/users/$userId/podcasts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"My Podcast","topic":"tech","sponsor":{"name":"Acme Corp","message":"building the future"}}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.sponsor.name").value("Acme Corp"))
            .andExpect(jsonPath("$.sponsor.message").value("building the future"))
    }

    @Test
    fun `create podcast with pronunciations`() {
        val podcastSlot = slot<Podcast>()
        every { userService.findById(userId) } returns user
        every { podcastService.create(userId, "My Podcast", "tech", capture(podcastSlot)) } answers {
            podcastSlot.captured.copy(id = podcastId)
        }

        mockMvc.perform(
            post("/users/$userId/podcasts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"My Podcast","topic":"tech","pronunciations":{"Anthropic":"/ænˈθɹɒpɪk/","Jarno":"/jɑrnoː/"}}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.pronunciations.Anthropic").value("/ænˈθɹɒpɪk/"))
            .andExpect(jsonPath("$.pronunciations.Jarno").value("/jɑrnoː/"))
    }

    @Test
    fun `update podcast with pronunciations`() {
        val existing = Podcast(
            id = podcastId, userId = userId, name = "My Podcast", topic = "tech"
        )
        val updatedSlot = slot<Podcast>()
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns existing
        every { podcastService.update(podcastId, capture(updatedSlot)) } answers {
            updatedSlot.captured
        }

        mockMvc.perform(
            put("/users/$userId/podcasts/$podcastId")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"My Podcast","topic":"tech","pronunciations":{"Stephan":"/ˈsteːfɑn/"}}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.pronunciations.Stephan").value("/ˈsteːfɑn/"))
    }

    @Test
    fun `get podcast includes pronunciations`() {
        val podcastWithPronunciations = Podcast(
            id = podcastId, userId = userId, name = "My Podcast", topic = "tech",
            pronunciations = mapOf("Anthropic" to "/ænˈθɹɒpɪk/", "Jarno" to "/jɑrnoː/")
        )
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns podcastWithPronunciations

        mockMvc.perform(get("/users/$userId/podcasts/$podcastId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.pronunciations.Anthropic").value("/ænˈθɹɒpɪk/"))
            .andExpect(jsonPath("$.pronunciations.Jarno").value("/jɑrnoː/"))
    }

    @Test
    fun `create podcast without optional fields uses defaults`() {
        val podcastSlot = slot<Podcast>()
        every { userService.findById(userId) } returns user
        every { podcastService.create(userId, "My Podcast", "tech", capture(podcastSlot)) } answers {
            podcastSlot.captured.copy(id = podcastId)
        }

        mockMvc.perform(
            post("/users/$userId/podcasts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"My Podcast","topic":"tech"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.relevanceThreshold").value(5))
            .andExpect(jsonPath("$.requireReview").value(false))
            .andExpect(jsonPath("$.ttsProvider").value("openai"))
            .andExpect(jsonPath("$.style").value("news-briefing"))
    }

    @Test
    fun `update podcast clears nullable fields when empty values sent`() {
        val existing = Podcast(
            id = podcastId, userId = userId, name = "My Podcast", topic = "tech",
            customInstructions = "Some instructions",
            sponsor = mapOf("name" to "Acme", "message" to "hello"),
            speakerNames = mapOf("host" to "Alice"),
            pronunciations = mapOf("Jarno" to "/jɑrnoː/")
        )
        val updatedSlot = slot<Podcast>()
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns existing
        every { podcastService.update(podcastId, capture(updatedSlot)) } answers {
            updatedSlot.captured
        }

        mockMvc.perform(
            put("/users/$userId/podcasts/$podcastId")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"My Podcast","topic":"tech","customInstructions":"","sponsor":{},"speakerNames":{},"pronunciations":{}}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.customInstructions").doesNotExist())
            .andExpect(jsonPath("$.sponsor").doesNotExist())
            .andExpect(jsonPath("$.speakerNames").doesNotExist())
            .andExpect(jsonPath("$.pronunciations").doesNotExist())
    }

    @Test
    fun `create podcast with subtopics and rapidFireWeightThreshold`() {
        val podcastSlot = slot<Podcast>()
        every { userService.findById(userId) } returns user
        every { podcastService.create(userId, "My Podcast", "tech", capture(podcastSlot)) } answers {
            podcastSlot.captured.copy(id = podcastId)
        }

        mockMvc.perform(
            post("/users/$userId/podcasts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"My Podcast","topic":"tech","subtopics":{"LLM releases":10,"Dev tools":5},"rapidFireWeightThreshold":4}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.subtopics.['LLM releases']").value(10))
            .andExpect(jsonPath("$.subtopics.['Dev tools']").value(5))
            .andExpect(jsonPath("$.rapidFireWeightThreshold").value(4))
    }

    @Test
    fun `create podcast defaults rapidFireWeightThreshold to 3`() {
        val podcastSlot = slot<Podcast>()
        every { userService.findById(userId) } returns user
        every { podcastService.create(userId, "My Podcast", "tech", capture(podcastSlot)) } answers {
            podcastSlot.captured.copy(id = podcastId)
        }

        mockMvc.perform(
            post("/users/$userId/podcasts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"My Podcast","topic":"tech"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.rapidFireWeightThreshold").value(3))
    }

    @Test
    fun `create podcast rejects weight 0`() {
        every { userService.findById(userId) } returns user
        mockMvc.perform(
            post("/users/$userId/podcasts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"x","topic":"y","subtopics":{"X":0}}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `create podcast rejects weight 11`() {
        every { userService.findById(userId) } returns user
        mockMvc.perform(
            post("/users/$userId/podcasts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"x","topic":"y","subtopics":{"X":11}}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `create podcast rejects empty subtopic name`() {
        every { userService.findById(userId) } returns user
        mockMvc.perform(
            post("/users/$userId/podcasts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"x","topic":"y","subtopics":{"":5}}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `create podcast rejects rapidFireWeightThreshold below 0`() {
        every { userService.findById(userId) } returns user
        mockMvc.perform(
            post("/users/$userId/podcasts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"x","topic":"y","rapidFireWeightThreshold":-1}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `create podcast rejects rapidFireWeightThreshold above 10`() {
        every { userService.findById(userId) } returns user
        mockMvc.perform(
            post("/users/$userId/podcasts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"x","topic":"y","rapidFireWeightThreshold":11}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `update podcast clears subtopics when empty map sent`() {
        val existing = Podcast(
            id = podcastId, userId = userId, name = "My Podcast", topic = "tech",
            subtopics = com.aisummarypodcast.store.Subtopics(mapOf("LLMs" to 10))
        )
        val updatedSlot = slot<Podcast>()
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns existing
        every { podcastService.update(podcastId, capture(updatedSlot)) } answers { updatedSlot.captured }

        mockMvc.perform(
            put("/users/$userId/podcasts/$podcastId")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"My Podcast","topic":"tech","subtopics":{}}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.subtopics").doesNotExist())
    }

    @Test
    fun `update podcast keeps nullable fields when not sent`() {
        val existing = Podcast(
            id = podcastId, userId = userId, name = "My Podcast", topic = "tech",
            customInstructions = "Some instructions",
            sponsor = mapOf("name" to "Acme", "message" to "hello")
        )
        val updatedSlot = slot<Podcast>()
        every { userService.findById(userId) } returns user
        every { podcastService.findById(podcastId) } returns existing
        every { podcastService.update(podcastId, capture(updatedSlot)) } answers {
            updatedSlot.captured
        }

        mockMvc.perform(
            put("/users/$userId/podcasts/$podcastId")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"My Podcast","topic":"tech"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.customInstructions").value("Some instructions"))
            .andExpect(jsonPath("$.sponsor.name").value("Acme"))
    }
}
