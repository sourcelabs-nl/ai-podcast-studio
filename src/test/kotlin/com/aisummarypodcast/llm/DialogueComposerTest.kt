package com.aisummarypodcast.llm

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
import org.junit.jupiter.api.Test

class DialogueComposerTest {

    private val appProperties = AppProperties(
        llm = LlmProperties(),
        briefing = BriefingProperties(targetWords = 1000),
        episodes = EpisodesProperties(),
        feed = FeedProperties(),
        encryption = EncryptionProperties(masterKey = "test-key")
    )

    private val composer = DialogueComposer(appProperties, mockk(), mockk(), PromptVarietyPicker())

    private val podcast = Podcast(
        id = "p1", userId = "u1", name = "Tech Talk", topic = "tech",
        style = PodcastStyle.DIALOGUE,
        ttsProvider = TtsProviderType.ELEVENLABS,
        ttsVoices = mapOf("host" to "v1", "cohost" to "v2"),
        fullBodyThreshold = 1
    )

    private val articles = listOf(
        Article(id = 1, sourceId = "s1", title = "AI News", body = "AI is advancing.", url = "https://example.com/ai", contentHash = "h1", summary = "AI progress."),
        Article(id = 2, sourceId = "s1", title = "Cloud News", body = "Cloud is growing.", url = "https://example.com/cloud", contentHash = "h2", summary = "Cloud growth.")
    )

    @Test
    fun `prompt instructs the model to call searchPastEpisodes before treating any topic as new`() {
        val prompt = composer.buildPrompt(articles, podcast)
        assertTrue(prompt.contains("searchPastEpisodes"))
        assertTrue(prompt.contains("HISTORY CHECK"))
    }

    @Test
    fun `prompt includes webSearch nudge when deepDiveEnabled, omits otherwise`() {
        val enabled = composer.buildPrompt(articles, podcast.copy(deepDiveEnabled = true))
        assertTrue(enabled.contains("webSearch"))
        assertTrue(enabled.contains("DEEP DIVE"))

        val disabled = composer.buildPrompt(articles, podcast.copy(deepDiveEnabled = false))
        assertFalse(disabled.contains("webSearch"))
        assertFalse(disabled.contains("DEEP DIVE"))
    }

    @Test
    fun `prompt includes speaker tag instructions`() {
        val prompt = composer.buildPrompt(articles, podcast)

        assertTrue(prompt.contains("<host>"))
        assertTrue(prompt.contains("<cohost>"))
        assertTrue(prompt.contains("host, cohost"))
    }

    @Test
    fun `prompt includes podcast metadata`() {
        val prompt = composer.buildPrompt(articles, podcast)

        assertTrue(prompt.contains("Tech Talk"))
        assertTrue(prompt.contains("tech"))
    }

    @Test
    fun `prompt includes article summaries`() {
        val prompt = composer.buildPrompt(articles, podcast)

        assertTrue(prompt.contains("AI progress."))
        assertTrue(prompt.contains("Cloud growth."))
    }

    @Test
    fun `prompt includes target word count`() {
        val prompt = composer.buildPrompt(articles, podcast)

        assertTrue(prompt.contains("1000"))
    }

    @Test
    fun `prompt includes custom instructions when set`() {
        val podcastWithInstructions = podcast.copy(customInstructions = "Focus on practical implications")
        val prompt = composer.buildPrompt(articles, podcastWithInstructions)

        assertTrue(prompt.contains("Focus on practical implications"))
    }

    @Test
    fun `prompt includes language instruction for non-English`() {
        val dutchPodcast = podcast.copy(language = "nl")
        val prompt = composer.buildPrompt(articles, dutchPodcast)

        assertTrue(prompt.contains("Dutch"))
    }

    @Test
    fun `prompt instructs all text must be inside tags`() {
        val prompt = composer.buildPrompt(articles, podcast)

        assertTrue(prompt.contains("ALL text MUST be inside speaker tags"))
    }

    @Test
    fun `prompt includes TTS guidelines when provided`() {
        val guidelines = "You MAY include emotion cues in square brackets."
        val prompt = composer.buildPrompt(articles, podcast, ttsScriptGuidelines = guidelines)

        assertTrue(prompt.contains("TTS script formatting:"))
        assertTrue(prompt.contains("emotion cues in square brackets"))
    }

    @Test
    fun `prompt omits TTS guidelines when empty`() {
        val prompt = composer.buildPrompt(articles, podcast, ttsScriptGuidelines = "")

        assertFalse(prompt.contains("TTS script formatting:"))
    }

    @Test
    fun `prompt does not include hardcoded emotion cues`() {
        val prompt = composer.buildPrompt(articles, podcast)

        assertFalse(prompt.contains("[cheerfully]"))
    }

    @Test
    fun `prompt includes follow-up annotation for continuation articles`() {
        val annotations = mapOf(1L to "Previously covered release details")
        val prompt = composer.buildPrompt(articles, podcast, followUpAnnotations = annotations)

        assertTrue(prompt.contains("[FOLLOW-UP: Previously covered release details]"))
    }

    @Test
    fun `prompt excludes follow-up annotation when empty`() {
        val prompt = composer.buildPrompt(articles, podcast)

        assertFalse(prompt.contains("[FOLLOW-UP:"))
    }

    @Test
    fun `prompt includes sponsor message when configured`() {
        val podcastWithSponsor = podcast.copy(sponsor = mapOf("name" to "Acme Corp", "message" to "building the future"))
        val prompt = composer.buildPrompt(articles, podcastWithSponsor)
        assertTrue(prompt.contains("This podcast is brought to you by Acme Corp"))
        assertTrue(prompt.contains("building the future"))
        assertTrue(prompt.contains("End with a sign-off that includes a mention of the sponsor: Acme Corp"))
    }

    @Test
    fun `prompt omits sponsor message when not configured`() {
        val prompt = composer.buildPrompt(articles, podcast)
        assertFalse(prompt.contains("sponsor"))
    }

    @Test
    fun `prompt includes grounding instruction`() {
        val prompt = composer.buildPrompt(articles, podcast)
        assertTrue(prompt.contains("ONLY discuss topics that are present in the article summaries"))
        assertTrue(prompt.contains("Do NOT introduce facts, stories, or claims from outside the provided articles"))
    }

    @Test
    fun `prompt includes engagement techniques`() {
        val prompt = composer.buildPrompt(articles, podcast)

        assertTrue(prompt.contains("HOOK OPENING"))
        assertTrue(prompt.contains("FRONT-LOAD THE BEST STORY"))
        assertTrue(prompt.contains("CURIOSITY HOOKS"))
        assertTrue(prompt.contains("MID-ROLL CALLBACKS"))
        assertTrue(prompt.contains("SHORT SEGMENTS WITH SIGNPOSTING"))
        assertTrue(prompt.contains("NATURAL INTERRUPTIONS"))
        assertTrue(prompt.contains("EMPHASIS ON IMPORTANT NEWS"))
    }

    @Test
    fun `prompt includes numbers-for-the-ear guidance`() {
        val prompt = composer.buildPrompt(articles, podcast)
        assertTrue(prompt.contains("NUMBERS FOR THE EAR"))
        assertTrue(prompt.contains("AT MOST ONE number per sentence or claim"))
    }

    @Test
    fun `prompt includes spoken model names guidance`() {
        val prompt = composer.buildPrompt(articles, podcast)
        assertTrue(prompt.contains("MODEL, PRODUCT & PACKAGE NAMES"))
        assertTrue(prompt.contains("May Code One Flash"))
    }

    @Test
    fun `prompt includes source-names-not-handles guidance`() {
        val prompt = composer.buildPrompt(articles, podcast)
        assertTrue(prompt.contains("SOURCE NAMES, NOT HANDLES"))
    }

    @Test
    fun `prompt includes research-names-for-the-ear guidance`() {
        val prompt = composer.buildPrompt(articles, podcast)
        assertTrue(prompt.contains("RESEARCH NAMES FOR THE EAR"))
    }

    @Test
    fun `prompt includes explain-for-non-experts guidance`() {
        val prompt = composer.buildPrompt(articles, podcast)
        assertTrue(prompt.contains("EXPLAIN FOR NON-EXPERTS"))
    }

    @Test
    fun `prompt allows TTS cues inside speaker tags`() {
        val prompt = composer.buildPrompt(articles, podcast)

        assertTrue(prompt.contains("Inside speaker tags, TTS-supported cues"))
        assertTrue(prompt.contains("ARE allowed"))
    }

    @Test
    fun `prompt includes speaker names when provided`() {
        val podcastWithNames = podcast.copy(
            speakerNames = mapOf("host" to "Sarah", "cohost" to "Mike")
        )
        val prompt = composer.buildPrompt(articles, podcastWithNames)

        assertTrue(prompt.contains("Sarah"))
        assertTrue(prompt.contains("Mike"))
        assertTrue(prompt.contains("Speaker names:"))
    }

    @Test
    fun `prompt does not include speaker names when absent`() {
        val prompt = composer.buildPrompt(articles, podcast)

        assertFalse(prompt.contains("Speaker names:"))
    }

    @Test
    fun `prompt uses full body when article count is below threshold`() {
        val podcastDefault = podcast.copy(fullBodyThreshold = null) // falls back to appProperties default of 5
        val fewArticles = listOf(
            Article(sourceId = "s1", title = "AI News", body = "Full AI body.", url = "https://example.com/ai", contentHash = "h1", summary = "AI summary.")
        )
        // Default threshold is 5, 1 article < 5 → use full body
        val prompt = composer.buildPrompt(fewArticles, podcastDefault)

        assertTrue(prompt.contains("Full AI body."))
        assertFalse(prompt.contains("AI summary."))
    }

    @Test
    fun `prompt uses summaries when article count is at or above threshold`() {
        val podcastDefault = podcast.copy(fullBodyThreshold = null) // falls back to appProperties default of 5
        val manyArticles = (1..5).map { i ->
            Article(sourceId = "s1", title = "News $i", body = "Body $i.", url = "https://example.com/$i", contentHash = "h$i", summary = "Summary $i.")
        }
        val prompt = composer.buildPrompt(manyArticles, podcastDefault)

        assertTrue(prompt.contains("Summary 1."))
        assertFalse(prompt.contains("Body 1."))
    }
}
