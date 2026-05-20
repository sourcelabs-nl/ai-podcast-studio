package com.aisummarypodcast.llm

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.config.BriefingProperties
import com.aisummarypodcast.llm.ResolvedModel
import com.aisummarypodcast.store.Article
import com.aisummarypodcast.store.Podcast
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.metadata.ChatResponseMetadata
import org.springframework.ai.chat.metadata.DefaultUsage
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation

class BriefingComposerTest {

    private val appProperties = mockk<AppProperties>().also {
        every { it.briefing } returns BriefingProperties(targetWords = 1500)
    }
    private val modelResolver = mockk<ModelResolver>()
    private val chatClientFactory = mockk<ChatClientFactory>()
    private val varietyPicker = PromptVarietyPicker()
    private val composer = BriefingComposer(appProperties, modelResolver, chatClientFactory, varietyPicker)

    @Test
    fun `stripSectionHeaders removes Opening header line`() {
        val input = "[Opening]\nWelcome to today's briefing.\n"
        val result = composer.stripSectionHeaders(input)
        assertEquals("Welcome to today's briefing.", result)
    }

    @Test
    fun `stripSectionHeaders removes multiple header lines`() {
        val input = "[Opening]\nWelcome.\n[Transition]\nNext topic.\n[Closing]\nThat's all.\n"
        val result = composer.stripSectionHeaders(input)
        assertEquals("Welcome.\nNext topic.\nThat's all.", result)
    }

    @Test
    fun `stripSectionHeaders preserves inline bracketed text`() {
        val input = "The company [ACME Corp] announced earnings.\n"
        val result = composer.stripSectionHeaders(input)
        assertEquals("The company [ACME Corp] announced earnings.", result)
    }

    @Test
    fun `stripSectionHeaders preserves brackets within sentences`() {
        val input = "Results were mixed [see chart] for the quarter.\nOverall performance improved.\n"
        val result = composer.stripSectionHeaders(input)
        assertEquals("Results were mixed [see chart] for the quarter.\nOverall performance improved.", result)
    }

    @Test
    fun `stripSectionHeaders handles script with no headers`() {
        val input = "Welcome to today's briefing.\nHere are the top stories.\n"
        val result = composer.stripSectionHeaders(input)
        assertEquals(input.trim(), result)
    }

    @Test
    fun `extractDomain extracts domain from standard URL`() {
        assertEquals("techcrunch.com", extractDomain("https://techcrunch.com/2026/02/12/example"))
    }

    @Test
    fun `extractDomain strips www prefix`() {
        assertEquals("theverge.com", extractDomain("https://www.theverge.com/article"))
    }

    @Test
    fun `extractDomain handles URL without path`() {
        assertEquals("example.com", extractDomain("https://example.com"))
    }

    @Test
    fun `extractDomain returns original string for invalid URL`() {
        assertEquals("not-a-url", extractDomain("not-a-url"))
    }

    @Test
    fun `extractDomain handles http URL`() {
        assertEquals("blog.example.org", extractDomain("http://blog.example.org/posts/123"))
    }

    @Test
    fun `summary block includes source domain`() {
        val articles = listOf(
            Article(
                id = 1,
                sourceId = "src-1",
                title = "AI Breakthrough",
                body = "body",
                url = "https://techcrunch.com/2026/02/12/ai",
                contentHash = "hash1",
                summary = "A major AI breakthrough was announced."
            ),
            Article(
                id = 2,
                sourceId = "src-2",
                title = "New Chip",
                body = "body",
                url = "https://www.theverge.com/chip",
                contentHash = "hash2",
                summary = "A new chip was unveiled."
            )
        )

        val podcast = Podcast(id = "p1", userId = "u1", name = "Test", topic = "tech")
        val prompt = composer.buildPrompt(articles, podcast)

        assertTrue(prompt.contains("[techcrunch.com] AI Breakthrough"))
        assertTrue(prompt.contains("[theverge.com] New Chip"))
    }

    @Test
    fun `summary block includes author when available`() {
        val articles = listOf(
            Article(
                id = 1,
                sourceId = "src-1",
                title = "AI Breakthrough",
                body = "body",
                url = "https://techcrunch.com/2026/02/12/ai",
                contentHash = "hash1",
                summary = "A major AI breakthrough was announced.",
                author = "John Smith"
            )
        )

        val podcast = Podcast(id = "p1", userId = "u1", name = "Test", topic = "tech")
        val prompt = composer.buildPrompt(articles, podcast)

        assertTrue(prompt.contains("[techcrunch.com, by John Smith] AI Breakthrough"))
    }

    @Test
    fun `summary block omits author when null`() {
        val articles = listOf(
            Article(
                id = 1,
                sourceId = "src-1",
                title = "AI Breakthrough",
                body = "body",
                url = "https://techcrunch.com/2026/02/12/ai",
                contentHash = "hash1",
                summary = "A major AI breakthrough was announced.",
                author = null
            )
        )

        val podcast = Podcast(id = "p1", userId = "u1", name = "Test", topic = "tech")
        val prompt = composer.buildPrompt(articles, podcast)

        assertTrue(prompt.contains("[techcrunch.com] AI Breakthrough"))
        assertFalse(prompt.contains(", by"))
    }

    private val sampleArticles = listOf(
        Article(
            id = 1, sourceId = "src-1", title = "AI News",
            body = "body", url = "https://techcrunch.com/ai",
            contentHash = "hash1", summary = "AI summary."
        )
    )

    @Test
    fun `buildPrompt instructs the model to call searchPastEpisodes before treating any topic as new`() {
        val podcast = Podcast(id = "p1", userId = "u1", name = "Tech", topic = "tech", language = "en")
        val prompt = composer.buildPrompt(sampleArticles, podcast)
        assertTrue(prompt.contains("searchPastEpisodes"), "Expected the history lookup tool name in the prompt")
        assertTrue(prompt.contains("HISTORY CHECK"), "Expected the HISTORY CHECK directive in the prompt")
    }

    @Test
    fun `buildPrompt includes webSearch nudge when deepDiveEnabled`() {
        val podcast = Podcast(id = "p1", userId = "u1", name = "Tech", topic = "tech", deepDiveEnabled = true)
        val prompt = composer.buildPrompt(sampleArticles, podcast)
        assertTrue(prompt.contains("webSearch"), "Expected webSearch tool name in prompt when deep-dive enabled")
        assertTrue(prompt.contains("DEEP DIVE"), "Expected DEEP DIVE directive in prompt")
    }

    @Test
    fun `buildPrompt omits webSearch nudge when deepDiveEnabled is false`() {
        val podcast = Podcast(id = "p1", userId = "u1", name = "Tech", topic = "tech", deepDiveEnabled = false)
        val prompt = composer.buildPrompt(sampleArticles, podcast)
        assertFalse(prompt.contains("webSearch"), "Did not expect webSearch tool in prompt when deep-dive disabled")
        assertFalse(prompt.contains("DEEP DIVE"))
    }

    @Test
    fun `buildPrompt includes language instruction for non-English podcast`() {
        val podcast = Podcast(id = "p1", userId = "u1", name = "Tech NL", topic = "tech", language = "nl")
        val prompt = composer.buildPrompt(sampleArticles, podcast)
        assertTrue(prompt.contains("Write the entire script in Dutch"), "Expected Dutch language instruction in prompt")
    }

    @Test
    fun `buildPrompt does not include language instruction for English podcast`() {
        val podcast = Podcast(id = "p1", userId = "u1", name = "Tech EN", topic = "tech", language = "en")
        val prompt = composer.buildPrompt(sampleArticles, podcast)
        assertFalse(prompt.contains("Write the entire script in"), "Expected no language instruction for English")
    }

    @Test
    fun `buildPrompt formats date in podcast language locale`() {
        val podcast = Podcast(id = "p1", userId = "u1", name = "Tech FR", topic = "tech", language = "fr")
        val prompt = composer.buildPrompt(sampleArticles, podcast)
        val dateLine = prompt.lines().find { it.trim().startsWith("Date:") }
        assertNotNull(dateLine, "Expected a Date line in the prompt")
        val englishDays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        val containsEnglishDay = englishDays.any { dateLine!!.contains(it) }
        assertFalse(containsEnglishDay, "Expected French date formatting, but found English day name in: $dateLine")
    }

    @Test
    fun `buildPrompt includes language instruction for French podcast`() {
        val podcast = Podcast(id = "p1", userId = "u1", name = "Tech FR", topic = "tech", language = "fr")
        val prompt = composer.buildPrompt(sampleArticles, podcast)
        assertTrue(prompt.contains("Write the entire script in French"))
    }

    @Test
    fun `buildPrompt uses summary when available and above threshold`() {
        val podcast = Podcast(id = "p1", userId = "u1", name = "Test", topic = "tech", fullBodyThreshold = 1)
        val articles = listOf(
            Article(
                id = 1, sourceId = "s1", title = "Long Article",
                body = "This is the full article body that is quite long.",
                url = "https://example.com/1", contentHash = "h1",
                summary = "This is the summary."
            )
        )
        val prompt = composer.buildPrompt(articles, podcast)
        assertTrue(prompt.contains("This is the summary."))
        assertFalse(prompt.contains("This is the full article body"))
    }

    @Test
    fun `buildPrompt uses body when summary is null`() {
        val podcast = Podcast(id = "p1", userId = "u1", name = "Test", topic = "tech")
        val articles = listOf(
            Article(
                id = 1, sourceId = "s1", title = "Short Article",
                body = "Short article body content.",
                url = "https://example.com/1", contentHash = "h1",
                summary = null
            )
        )
        val prompt = composer.buildPrompt(articles, podcast)
        assertTrue(prompt.contains("Short article body content."))
    }

    @Test
    fun `compose returns CompositionResult with token usage`() {
        val composeModelDef = ResolvedModel(provider = "openrouter", model = "test-model", cost = null)
        val podcast = Podcast(id = "p1", userId = "u1", name = "Test Pod", topic = "tech")
        val articles = listOf(
            Article(
                id = 1, sourceId = "s1", title = "AI News", body = "body",
                url = "https://example.com/1", contentHash = "h1", summary = "AI summary."
            )
        )

        val metadata = ChatResponseMetadata.builder()
            .usage(DefaultUsage(1200, 400))
            .build()
        val chatResponse = ChatResponse(listOf(Generation(AssistantMessage("Welcome to the briefing."))), metadata)

        val callResponseSpec = mockk<ChatClient.CallResponseSpec>()
        every { callResponseSpec.chatResponse() } returns chatResponse

        val chatClientRequestSpec = mockk<ChatClient.ChatClientRequestSpec>()
        every { chatClientRequestSpec.user(any<String>()) } returns chatClientRequestSpec
        every { chatClientRequestSpec.options(any()) } returns chatClientRequestSpec
        every { chatClientRequestSpec.call() } returns callResponseSpec

        val chatClient = mockk<ChatClient>()
        every { chatClient.prompt() } returns chatClientRequestSpec
        every { chatClientFactory.createForCompose(podcast.userId, composeModelDef, podcast, any()) } returns chatClient

        val result = composer.compose(articles, podcast, composeModelDef)

        assertEquals("Welcome to the briefing.", result.script)
        assertEquals(1200, result.usage.inputTokens)
        assertEquals(400, result.usage.outputTokens)
    }

    @Test
    fun `buildPrompt includes follow-up annotation for continuation articles`() {
        val podcast = Podcast(id = "p1", userId = "u1", name = "Test", topic = "tech")
        val annotations = mapOf(1L to "Previously covered Gemini 2.5 release and benchmarks")
        val prompt = composer.buildPrompt(sampleArticles, podcast, followUpAnnotations = annotations)
        assertTrue(prompt.contains("[FOLLOW-UP: Previously covered Gemini 2.5 release and benchmarks]"))
    }

    @Test
    fun `buildPrompt excludes follow-up annotation when empty`() {
        val podcast = Podcast(id = "p1", userId = "u1", name = "Test", topic = "tech")
        val prompt = composer.buildPrompt(sampleArticles, podcast)
        assertFalse(prompt.contains("[FOLLOW-UP:"))
    }

    @Test
    fun `buildPrompt includes grounding instruction`() {
        val podcast = Podcast(id = "p1", userId = "u1", name = "Test", topic = "tech")
        val prompt = composer.buildPrompt(sampleArticles, podcast)
        assertTrue(prompt.contains("ONLY discuss topics that are present in the article summaries"))
        assertTrue(prompt.contains("Do NOT introduce facts, stories, or claims from outside the provided articles"))
    }

    @Test
    fun `buildPrompt includes sponsor message when configured`() {
        val podcast = Podcast(
            id = "p1", userId = "u1", name = "Test", topic = "tech",
            sponsor = mapOf("name" to "Acme Corp", "message" to "building the future")
        )
        val prompt = composer.buildPrompt(sampleArticles, podcast)
        assertTrue(prompt.contains("This podcast is brought to you by Acme Corp"))
        assertTrue(prompt.contains("building the future"))
        assertTrue(prompt.contains("End with a sign-off that includes a mention of the sponsor: Acme Corp"))
    }

    @Test
    fun `buildPrompt omits sponsor message when not configured`() {
        val podcast = Podcast(id = "p1", userId = "u1", name = "Test", topic = "tech")
        val prompt = composer.buildPrompt(sampleArticles, podcast)
        assertFalse(prompt.contains("sponsor"))
    }

    @Test
    fun `buildPrompt includes author attribution instruction`() {
        val podcast = Podcast(id = "p1", userId = "u1", name = "Test", topic = "tech")
        val prompt = composer.buildPrompt(sampleArticles, podcast)
        assertTrue(prompt.contains("credit original authors when known"))
    }

    @Test
    fun `buildPrompt includes engagement techniques`() {
        val podcast = Podcast(id = "p1", userId = "u1", name = "Test", topic = "tech")
        val prompt = composer.buildPrompt(sampleArticles, podcast)

        assertTrue(prompt.contains("HOOK OPENING"))
        assertTrue(prompt.contains("FRONT-LOAD THE BEST STORY"))
        assertTrue(prompt.contains("SHORT SEGMENTS WITH SIGNPOSTING"))
        assertTrue(prompt.contains("EMPHASIS ON IMPORTANT NEWS"))
    }

    @Test
    fun `buildPrompt allows TTS cues inside content`() {
        val podcast = Podcast(id = "p1", userId = "u1", name = "Test", topic = "tech")
        val prompt = composer.buildPrompt(sampleArticles, podcast)

        assertTrue(prompt.contains("TTS-supported cues"))
        assertTrue(prompt.contains("ARE allowed"))
    }

    @Test
    fun `buildPrompt handles mixed summary and body articles above threshold`() {
        val podcast = Podcast(id = "p1", userId = "u1", name = "Test", topic = "tech", fullBodyThreshold = 1)
        val articles = listOf(
            Article(
                id = 1, sourceId = "s1", title = "Long Article",
                body = "Long body text.",
                url = "https://example.com/1", contentHash = "h1",
                summary = "Summary of long article."
            ),
            Article(
                id = 2, sourceId = "s1", title = "Short Article",
                body = "Short body text.",
                url = "https://example.com/2", contentHash = "h2",
                summary = null
            )
        )
        val prompt = composer.buildPrompt(articles, podcast)
        assertTrue(prompt.contains("Summary of long article."))
        assertTrue(prompt.contains("Short body text."))
        assertFalse(prompt.contains("Long body text."))
    }

    @Test
    fun `buildPrompt uses full body when article count is below threshold`() {
        val podcast = Podcast(id = "p1", userId = "u1", name = "Test", topic = "tech")
        val articles = listOf(
            Article(
                id = 1, sourceId = "s1", title = "Article 1",
                body = "Full body of article one.",
                url = "https://example.com/1", contentHash = "h1",
                summary = "Summary of article one."
            ),
            Article(
                id = 2, sourceId = "s1", title = "Article 2",
                body = "Full body of article two.",
                url = "https://example.com/2", contentHash = "h2",
                summary = "Summary of article two."
            )
        )
        // Default threshold is 5, 2 articles < 5 → use full body
        val prompt = composer.buildPrompt(articles, podcast)
        assertTrue(prompt.contains("Full body of article one."))
        assertTrue(prompt.contains("Full body of article two."))
        assertFalse(prompt.contains("Summary of article one."))
        assertFalse(prompt.contains("Summary of article two."))
    }

    @Test
    fun `buildPrompt uses summaries when article count is at or above threshold`() {
        val podcast = Podcast(id = "p1", userId = "u1", name = "Test", topic = "tech", fullBodyThreshold = 3)
        val articles = (1..3).map { i ->
            Article(
                id = i.toLong(), sourceId = "s1", title = "Article $i",
                body = "Full body $i.",
                url = "https://example.com/$i", contentHash = "h$i",
                summary = "Summary $i."
            )
        }
        // 3 articles, threshold 3 → not below threshold → use summaries
        val prompt = composer.buildPrompt(articles, podcast)
        assertTrue(prompt.contains("Summary 1."))
        assertTrue(prompt.contains("Summary 2."))
        assertTrue(prompt.contains("Summary 3."))
        assertFalse(prompt.contains("Full body 1."))
    }

    @Test
    fun `buildPrompt includes TTS guidelines when provided`() {
        val podcast = Podcast(id = "p1", userId = "u1", name = "Test", topic = "tech")
        val guidelines = "Use *word* for emphasis and [laugh] for non-verbal cues."
        val prompt = composer.buildPrompt(sampleArticles, podcast, ttsScriptGuidelines = guidelines)
        assertTrue(prompt.contains("TTS script formatting:"))
        assertTrue(prompt.contains("Use *word* for emphasis"))
    }

    @Test
    fun `buildPrompt omits TTS guidelines when empty`() {
        val podcast = Podcast(id = "p1", userId = "u1", name = "Test", topic = "tech")
        val prompt = composer.buildPrompt(sampleArticles, podcast, ttsScriptGuidelines = "")
        assertFalse(prompt.contains("TTS script formatting:"))
    }

    @Test
    fun `buildPrompt respects per-podcast fullBodyThreshold`() {
        val podcast = Podcast(id = "p1", userId = "u1", name = "Test", topic = "tech", fullBodyThreshold = 2)
        val articles = listOf(
            Article(
                id = 1, sourceId = "s1", title = "Article 1",
                body = "Full body text.",
                url = "https://example.com/1", contentHash = "h1",
                summary = "Summary text."
            ),
            Article(
                id = 2, sourceId = "s1", title = "Article 2",
                body = "Full body text 2.",
                url = "https://example.com/2", contentHash = "h2",
                summary = "Summary text 2."
            )
        )
        // 2 articles, threshold 2 → not below → use summaries
        val prompt = composer.buildPrompt(articles, podcast)
        assertTrue(prompt.contains("Summary text."))
        assertFalse(prompt.contains("Full body text."))
    }
}
