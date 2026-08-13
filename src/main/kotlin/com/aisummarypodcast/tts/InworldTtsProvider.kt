package com.aisummarypodcast.tts

import com.aisummarypodcast.store.PodcastStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger

@Component
class InworldTtsProvider(
    private val apiClient: InworldApiClient
) : TtsProvider {

    private val log = LoggerFactory.getLogger(javaClass)

    override val maxChunkSize: Int = 2000

    companion object {
        const val DEFAULT_MODEL = "inworld-tts-1.5-max"
        private const val DEFAULT_TEMPERATURE = 0.8
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val MAX_CONCURRENCY = 5
        private val RETRY_DELAYS_MS = longArrayOf(1000, 2000, 4000)

        private val CORE_GUIDELINES = $$"""
            |The TTS engine supports rich expressiveness markup:
            |- Non-verbal tags: [sigh], [laugh], [breathe], [cough], [clear_throat], [yawn] — use sparingly for natural effect
            |- Emphasis: use *word* (single asterisks) for stressed words. NEVER use **double asterisks** — the TTS engine will read the asterisk characters aloud
            |- Pacing: use ellipsis (...) for trailing pauses, exclamation marks for excitement
            |Text formatting rules:
            |- Write all numbers, dates, currencies, and symbols in fully spoken form (e.g. "twenty twenty-six" not "2026", "five thousand dollars" not "$5,000", "ten percent" not "10%")
            |- NEVER use markdown formatting (headers, bold, bullet points, links) — write everything as natural spoken sentences
            |- Use natural contractions throughout (don't, we're, it's, they've) for spoken naturalness
            |- Always end sentences with proper punctuation (period, question mark, or exclamation mark) — the TTS engine uses these for pacing""".trimMargin()

        private val CASUAL_ADDITION = """
            |- Use natural filler words (uh, um, well, you know) to sound conversational and human""".trimMargin()

        private val FORMAL_ADDITION = """
            |- Avoid filler words (uh, um, well, you know) and minimize non-verbal tags — keep delivery clean and professional""".trimMargin()
    }

    override fun scriptGuidelines(style: PodcastStyle, pronunciations: Map<String, String>): String {
        val styleSpecific = when (style) {
            PodcastStyle.CASUAL, PodcastStyle.DIALOGUE -> CASUAL_ADDITION
            PodcastStyle.EXECUTIVE_SUMMARY, PodcastStyle.NEWS_BRIEFING -> FORMAL_ADDITION
            else -> ""
        }
        val base = if (styleSpecific.isNotEmpty()) "$CORE_GUIDELINES\n$styleSpecific" else CORE_GUIDELINES
        if (pronunciations.isEmpty()) return base
        val pronunciationGuide = buildString {
            appendLine()
            appendLine("Pronunciation Guide:")
            appendLine("On EVERY occurrence of each term below, REPLACE the word with its IPA phoneme notation (e.g. write /jɑrnoː/ instead of Jarno). Do NOT write both the word and the phoneme — only the phoneme.")
            appendLine("IMPORTANT: Place the IPA phoneme naturally in the sentence flow. Do NOT add commas, pauses, or extra punctuation around the phoneme. When addressing someone by name (vocative), place the name at the START of the sentence so the comma follows naturally. Wrong: \"you teased, /jɑrnoː/, because\" — Right: \"you teased /jɑrnoː/ because\". Wrong: \"What do you think, /jɑrnoː/?\" — Right: \"/jɑrnoː/, what do you think?\".")
            appendLine("CRITICAL: ONLY use IPA notation for the exact terms listed below. Do NOT invent or add IPA pronunciation for ANY other words. If a word is not in the list below, write it normally.")
            for ((term, ipa) in pronunciations) {
                appendLine("- $term → $ipa")
            }
        }.trimEnd()
        return "$base\n$pronunciationGuide"
    }

    override suspend fun generate(request: TtsRequest): TtsResult {
        val modelId = request.ttsSettings["model"] ?: DEFAULT_MODEL
        val deliveryMode = request.ttsSettings["deliveryMode"]?.takeIf { it.isNotBlank() }?.uppercase()
        val options = InworldSynthesisOptions(
            speed = request.ttsSettings["speed"]?.toDoubleOrNull(),
            // deliveryMode replaces temperature on TTS-2 — only default temperature when deliveryMode is unset
            temperature = if (deliveryMode != null) request.ttsSettings["temperature"]?.toDoubleOrNull()
                          else request.ttsSettings["temperature"]?.toDoubleOrNull() ?: DEFAULT_TEMPERATURE,
            deliveryMode = deliveryMode,
            enhanceGeneration = request.ttsSettings["enhanceGeneration"]?.toBooleanStrictOrNull()
        )
        val style = inferStyle(request)

        return if (style == PodcastStyle.DIALOGUE || style == PodcastStyle.INTERVIEW) {
            generateDialogue(request, modelId, options)
        } else {
            generateMonologue(request, modelId, options)
        }
    }

    private suspend fun generateMonologue(request: TtsRequest, modelId: String, options: InworldSynthesisOptions): TtsResult {
        val voiceId = request.ttsVoices["default"]
            ?: throw IllegalStateException("Inworld TTS requires a 'default' voice in ttsVoices")

        val processedScript = InworldScriptPostProcessor.process(request.script)
        val chunks = TextChunker.chunk(processedScript, maxChunkSize)
        log.info("Generating Inworld TTS audio for {} chunks in parallel (voice: {}, model: {}, options: {})", chunks.size, voiceId, modelId, options)

        val totalCharacters = AtomicInteger(0)
        val semaphore = Semaphore(MAX_CONCURRENCY)
        val audioChunks = withContext(Dispatchers.IO) {
            chunks.mapIndexed { index, chunk ->
                async {
                    semaphore.withPermit {
                        log.info("Generating Inworld TTS chunk {}/{} ({} chars)", index + 1, chunks.size, chunk.length)
                        val response = synthesizeWithRetry(request.userId, voiceId, chunk, modelId, options)
                        totalCharacters.addAndGet(response.processedCharactersCount)
                        Base64.getDecoder().decode(response.audioContent)
                    }
                }
            }.awaitAll()
        }

        return TtsResult(
            audioChunks = audioChunks,
            totalCharacters = totalCharacters.get(),
            requiresConcatenation = chunks.size > 1,
            model = modelId
        )
    }

    private suspend fun generateDialogue(request: TtsRequest, modelId: String, options: InworldSynthesisOptions): TtsResult {
        val turns = DialogueScriptParser.parse(request.script)
        if (turns.isEmpty()) {
            throw IllegalStateException("Dialogue script produced no speaker turns")
        }

        // Flatten all turn chunks into a single indexed list for full parallel generation
        data class ChunkWork(val voiceId: String, val text: String)

        val allChunks = turns.flatMapIndexed { index, turn ->
            val voiceId = request.ttsVoices[turn.role]
                ?: throw IllegalStateException(
                    "No voice configured for role '${turn.role}'. Available roles: ${request.ttsVoices.keys.joinToString()}"
                )
            val processedText = InworldScriptPostProcessor.process(turn.text)
            val turnChunks = TextChunker.chunk(processedText, maxChunkSize)
            log.info("Inworld dialogue turn {}/{} (role: {}, {} chunks, {} chars)", index + 1, turns.size, turn.role, turnChunks.size, turn.text.length)
            turnChunks.map { chunk -> ChunkWork(voiceId, chunk) }
        }

        log.info("Generating Inworld dialogue: {} total chunks in parallel, model: {}, options: {}", allChunks.size, modelId, options)

        val totalCharacters = AtomicInteger(0)
        val semaphore = Semaphore(MAX_CONCURRENCY)
        val audioChunks = withContext(Dispatchers.IO) {
            allChunks.mapIndexed { index, work ->
                async {
                    semaphore.withPermit {
                        log.info("Generating Inworld dialogue chunk {}/{} ({} chars)", index + 1, allChunks.size, work.text.length)
                        val response = synthesizeWithRetry(request.userId, work.voiceId, work.text, modelId, options)
                        totalCharacters.addAndGet(response.processedCharactersCount)
                        Base64.getDecoder().decode(response.audioContent)
                    }
                }
            }.awaitAll()
        }

        return TtsResult(
            audioChunks = audioChunks,
            totalCharacters = totalCharacters.get(),
            requiresConcatenation = audioChunks.size > 1,
            model = modelId
        )
    }

    private suspend fun synthesizeWithRetry(
        userId: String, voiceId: String, text: String, modelId: String, options: InworldSynthesisOptions
    ): InworldSpeechResponse {
        for (attempt in 0 until MAX_RETRY_ATTEMPTS) {
            try {
                return apiClient.synthesizeSpeech(userId, voiceId, text, modelId, options)
            } catch (e: InworldRateLimitException) {
                if (attempt == MAX_RETRY_ATTEMPTS - 1) throw e
                val delayMs = RETRY_DELAYS_MS[attempt]
                log.warn("Inworld rate limited (attempt {}/{}), retrying in {}ms", attempt + 1, MAX_RETRY_ATTEMPTS, delayMs)
                delay(delayMs)
            } catch (e: ResourceAccessException) {
                // Transient I/O failure (connection reset, timeout) — retry on a fresh connection
                if (attempt == MAX_RETRY_ATTEMPTS - 1) throw e
                val delayMs = RETRY_DELAYS_MS[attempt]
                log.warn("Inworld I/O error '{}' (attempt {}/{}), retrying in {}ms", e.message, attempt + 1, MAX_RETRY_ATTEMPTS, delayMs)
                delay(delayMs)
            } catch (e: InworldTransientException) {
                // Transient server-side failure (HTTP 5xx, e.g. a brief 503 upstream outage) — retry
                if (attempt == MAX_RETRY_ATTEMPTS - 1) throw e
                val delayMs = RETRY_DELAYS_MS[attempt]
                log.warn("Inworld transient error '{}' (attempt {}/{}), retrying in {}ms", e.message, attempt + 1, MAX_RETRY_ATTEMPTS, delayMs)
                delay(delayMs)
            }
        }
        throw IllegalStateException("Unreachable")
    }

    private fun inferStyle(request: TtsRequest): PodcastStyle? {
        // If ttsVoices has roles other than "default", it's a dialogue/interview style
        val roles = request.ttsVoices.keys
        return when {
            roles.contains("interviewer") && roles.contains("expert") -> PodcastStyle.INTERVIEW
            roles.size > 1 && !roles.all { it == "default" } -> PodcastStyle.DIALOGUE
            else -> null
        }
    }
}
