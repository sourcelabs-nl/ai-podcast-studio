package com.aisummarypodcast.llm

import tools.jackson.databind.json.JsonMapper
import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.BriefingProperties
import com.aisummarypodcast.config.EncryptionProperties
import com.aisummarypodcast.config.EpisodesProperties
import com.aisummarypodcast.config.FeedProperties
import com.aisummarypodcast.config.LlmProperties
import com.aisummarypodcast.store.Article
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.PodcastStyle
import com.aisummarypodcast.store.TtsProviderType
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Guards against verbatim example phrases creeping back into composer prompts. The LLM was
 * copying these word-for-word into generated scripts, which is exactly what `add-script-variety`
 * removes. Fails CI if any banned phrase reappears in any composer prompt.
 */
class ComposerBannedPromptsTest {

    private val appProperties = AppProperties(
        llm = LlmProperties(),
        briefing = BriefingProperties(targetWords = 1500),
        episodes = EpisodesProperties(),
        feed = FeedProperties(),
        encryption = EncryptionProperties(masterKey = "test-key")
    )
    private val varietyPicker = PromptVarietyPicker()

    private val sampleArticles = (1..6).map { i ->
        Article(
            id = i.toLong(), sourceId = "s1", title = "Article $i",
            body = "Body $i.", url = "https://example.com/$i",
            contentHash = "h$i", summary = "Summary $i."
        )
    }

    @ParameterizedTest
    @EnumSource(PodcastStyle::class)
    fun `built compose prompt contains no banned example phrases`(style: PodcastStyle) {
        val prompt = buildPromptFor(style)
        BannedPrompts.phrases.forEach { banned ->
            assertFalse(
                prompt.contains(banned),
                "Compose prompt for style $style still contains banned phrase: \"$banned\""
            )
        }
    }

    @ParameterizedTest
    @EnumSource(PodcastStyle::class)
    fun `built compose prompt subordinates past coverage to the follow-up annotations`(style: PodcastStyle) {
        val prompt = buildPromptFor(style, withHistory = true)

        // Dedup compares titles and summaries against the real historical article set; the history
        // search only matches keywords against past scripts. Told to treat any hit as prior coverage, the
        // composer claimed a launch had been "covered yesterday" and dropped it from the lead.
        assertTrue(
            prompt.contains("WHAT COUNTS AS NEW"),
            "Compose prompt for style $style must state that the [FOLLOW-UP: ...] headers are authoritative"
        )
        assertTrue(
            prompt.contains("A match is NOT evidence"),
            "Compose prompt for style $style must forbid demoting a story on a keyword match alone"
        )
    }

    private fun buildPromptFor(style: PodcastStyle, withHistory: Boolean = false): String {
        val podcast = podcastForStyle(style)
        val context = if (!withHistory) ComposeContext() else ComposeContext(
            research = com.aisummarypodcast.research.PreComposeResearch(
                history = listOf(PastEpisodeMatch(1, "2026-09-01", "GPT-6", "Benchmarks leaked."))
            )
        )
        return when (style) {
            PodcastStyle.DIALOGUE ->
                DialogueComposer(appProperties, mockk(), mockk(), varietyPicker, TopicOrderExtractor(JsonMapper.builder().build())).buildPrompt(sampleArticles, podcast, context)

            PodcastStyle.INTERVIEW ->
                InterviewComposer(appProperties, mockk(), mockk(), varietyPicker, TopicOrderExtractor(JsonMapper.builder().build())).buildPrompt(sampleArticles, podcast, context)

            else ->
                BriefingComposer(appProperties, mockk(), mockk(), varietyPicker, TopicOrderExtractor(JsonMapper.builder().build())).buildPrompt(sampleArticles, podcast, context)
        }
    }

    private fun podcastForStyle(style: PodcastStyle): Podcast = when (style) {
        PodcastStyle.DIALOGUE -> Podcast(
            id = "p-dialogue", userId = "u1", name = "Test", topic = "tech",
            style = PodcastStyle.DIALOGUE,
            ttsProvider = TtsProviderType.ELEVENLABS,
            ttsVoices = mapOf("host" to "v1", "cohost" to "v2")
        )

        PodcastStyle.INTERVIEW -> Podcast(
            id = "p-interview", userId = "u1", name = "Test", topic = "tech",
            style = PodcastStyle.INTERVIEW,
            ttsProvider = TtsProviderType.ELEVENLABS,
            ttsVoices = mapOf("interviewer" to "v1", "expert" to "v2")
        )

        else -> Podcast(id = "p-${style.value}", userId = "u1", name = "Test", topic = "tech", style = style)
    }
}
