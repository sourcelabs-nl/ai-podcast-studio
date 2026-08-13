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

private data class ChunkWork(val voiceId: String, val text: String)

private data class SynthesisOutput(val audio: List<ByteArray>, val characters: Int)

@Component
class InworldTtsProvider(
    private val apiClient: InworldApiClient
) : TtsProvider {

    private val log = LoggerFactory.getLogger(javaClass)

    // 1900 rather than the API's 2000 limit, leaving room for a re-emitted steering instruction
    override val maxChunkSize: Int = 1900

    companion object {
        const val DEFAULT_MODEL = "inworld-tts-2"
        private const val DEFAULT_TEMPERATURE = 0.8
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val MAX_CONCURRENCY = 5
        private val RETRY_DELAYS_MS = longArrayOf(1000, 2000, 4000)

        /** Bounds on `synthesisContext.previousRequests`: enough for continuity, small enough to stay cheap. */
        private const val MAX_CONTEXT_REQUESTS = 3
        private const val MAX_CONTEXT_CHARS = 2000

        private val CORE_GUIDELINES = $$"""
            |The TTS engine supports rich expressiveness markup:
            |- Non-verbal tags: [sigh], [laugh], [breathe], [cough], [clear throat], [yawn] — use sparingly for natural effect. Spell them exactly as written; an unrecognised name is read as a delivery instruction instead of a sound
            |- Emphasis: use *word* (single asterisks) for stressed words, or write a whole word or a single syllable in CAPS for stronger stress (e.g. "that is ABSOLUTELY right", "AbsoLUTEly"). NEVER use **double asterisks** — the TTS engine will read the asterisk characters aloud
            |- Pacing: use ellipsis (...) for trailing pauses, exclamation marks for excitement
            |- Deliberate pauses: for a beat between segments use an SSML break tag such as <break time="1s" />. Use at most a handful per script (the engine honours 20 per request, each at most 10 seconds), and do not put one at a paragraph break, where the pause already exists
            |- Delivery direction: you may open a speaker turn or a new segment with ONE short English instruction in square brackets, e.g. [warm and conversational with an easy pace]. Put it at the very start, use at most one per turn, keep it consistent with what is being said, and write [reset] to return to neutral delivery
            |Text formatting rules:
            |- Write all numbers, dates, currencies, and symbols in fully spoken form (e.g. "twenty twenty-six" not "2026", "five thousand dollars" not "$5,000", "ten percent" not "10%")
            |- Acronyms: expand an acronym on first use, then use the short form. Write the short form as a word when it is pronounceable (NASA, GPT) and spell it out letter by letter when it is not (A-P-I, L-L-M) — automatic normalization does not cover domain acronyms
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
            enhanceGeneration = request.ttsSettings["enhanceGeneration"]?.toBooleanStrictOrNull(),
            language = request.language.takeIf { it.isNotBlank() }
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

        val chunks = prepareChunks(request.script, modelId)
        log.info("Generating Inworld TTS audio for {} chunks in parallel (voice: {}, model: {}, options: {})", chunks.size, voiceId, modelId, options)

        val audioChunks = synthesizeAll(request.userId, chunks.map { ChunkWork(voiceId, it) }, modelId, options)

        return TtsResult(
            audioChunks = audioChunks.audio,
            totalCharacters = audioChunks.characters,
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
        val allChunks = turns.flatMapIndexed { index, turn ->
            val voiceId = request.ttsVoices[turn.role]
                ?: throw IllegalStateException(
                    "No voice configured for role '${turn.role}'. Available roles: ${request.ttsVoices.keys.joinToString()}"
                )
            val turnChunks = prepareChunks(turn.text, modelId)
            log.info("Inworld dialogue turn {}/{} (role: {}, {} chunks, {} chars)", index + 1, turns.size, turn.role, turnChunks.size, turn.text.length)
            turnChunks.map { chunk -> ChunkWork(voiceId, chunk) }
        }

        log.info("Generating Inworld dialogue: {} total chunks in parallel, model: {}, options: {}", allChunks.size, modelId, options)

        val audioChunks = synthesizeAll(request.userId, allChunks, modelId, options)

        return TtsResult(
            audioChunks = audioChunks.audio,
            totalCharacters = audioChunks.characters,
            requiresConcatenation = audioChunks.audio.size > 1,
            model = modelId
        )
    }

    /**
     * Generates every chunk concurrently. Each request carries the text of the chunks that precede it
     * as `synthesisContext`, which is known up front because the whole script is chunked before
     * generation starts — so context costs no parallelism.
     */
    private suspend fun synthesizeAll(
        userId: String, work: List<ChunkWork>, modelId: String, options: InworldSynthesisOptions
    ): SynthesisOutput {
        val texts = work.map { it.text }
        val totalCharacters = AtomicInteger(0)
        val semaphore = Semaphore(MAX_CONCURRENCY)
        val audio = withContext(Dispatchers.IO) {
            work.mapIndexed { index, chunk ->
                async {
                    semaphore.withPermit {
                        log.info("Generating Inworld TTS chunk {}/{} ({} chars)", index + 1, work.size, chunk.text.length)
                        val chunkOptions = options.copy(previousRequests = contextWindow(texts.subList(0, index)))
                        val response = synthesizeWithRetry(userId, chunk.voiceId, chunk.text, modelId, chunkOptions)
                        totalCharacters.addAndGet(response.processedCharactersCount)
                        Base64.getDecoder().decode(response.audioContent)
                    }
                }
            }.awaitAll()
        }
        return SynthesisOutput(audio, totalCharacters.get())
    }

    /** Post-processes, chunks, and keeps any steering instruction alive across the chunk splices. */
    private fun prepareChunks(text: String, modelId: String): List<String> {
        val supportsSteering = InworldSteering.supportsSteering(modelId)
        val processed = InworldScriptPostProcessor.process(text, retainSteeringInstructions = supportsSteering)
        val chunks = TextChunker.chunk(processed, maxChunkSize)
        return if (supportsSteering) InworldSteering.reemitInstructions(chunks) else chunks
    }

    /** The most recent preceding texts that fit within both bounds, oldest first. */
    private fun contextWindow(preceding: List<String>): List<String> {
        val window = ArrayDeque<String>()
        var budget = MAX_CONTEXT_CHARS
        for (text in preceding.asReversed()) {
            if (window.size >= MAX_CONTEXT_REQUESTS || text.length > budget) break
            window.addFirst(text)
            budget -= text.length
        }
        return window.toList()
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
