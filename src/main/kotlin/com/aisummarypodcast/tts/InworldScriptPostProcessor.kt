package com.aisummarypodcast.tts

import org.slf4j.LoggerFactory

object InworldScriptPostProcessor {

    private val log = LoggerFactory.getLogger(InworldScriptPostProcessor::class.java)

    /** Sound names Inworld documents. An unrecognised name is reinterpreted as a steering instruction. */
    private val SOUND_TAGS = setOf("sigh", "laugh", "breathe", "cough", "clear throat", "yawn")

    /**
     * Instruction words asking for a delivery that removes expression or reduces audibility. The
     * engine obeys a steering instruction literally, so these are dropped rather than forwarded.
     *
     * `[deadpan]` on an expert turn a quarter of the way into episode 194 produced some 25 seconds
     * of flat, expressionless delivery that a listener noticed. Every other cue the composer has
     * produced across recent episodes added warmth or energy — warm and conversational, playful,
     * bright and quick, with quiet awe — so the mechanism is sound and only the vocabulary needed a
     * floor.
     *
     * Stripping is deliberately the safe direction: the turn falls back to neutral delivery, which
     * is never wrong, so a false positive costs a little colour while a false negative costs part of
     * an episode. `flat` is listed on that basis, even though it could appear in a benign phrase.
     */
    private val FLATTENING_INSTRUCTION_WORDS = setOf(
        "deadpan", "monotone", "monotonous", "flat", "flatly",
        "robotic", "robotically", "mechanical", "mechanically",
        "emotionless", "emotionlessly", "expressionless", "lifeless", "lifelessly",
        "dull", "dully", "bored", "boredly", "disinterested", "uninterested",
        "whisper", "whispers", "whispering", "whispered",
        "mutter", "muttering", "muttered", "mumble", "mumbling", "mumbled",
        "shout", "shouting", "shouted", "scream", "screaming", "yell", "yelling",
    )

    /** Splits an instruction into comparable words; the match is per word, not per substring. */
    private val INSTRUCTION_WORDS = Regex("[^A-Za-z]+")

    private val DOUBLE_ASTERISKS = Regex("\\*\\*(.+?)\\*\\*")
    private val MARKDOWN_HEADERS = Regex("(?m)^#{1,6}\\s+.*$")
    private val MARKDOWN_BULLETS = Regex("(?m)^[-*]\\s+")
    private val MARKDOWN_LINKS = Regex("\\[([^]]+)]\\([^)]+\\)")
    private val EMOJIS = Regex("[\\x{1F600}-\\x{1F64F}\\x{1F300}-\\x{1F5FF}\\x{1F680}-\\x{1F6FF}\\x{1F1E0}-\\x{1F1FF}\\x{2600}-\\x{27BF}\\x{2300}-\\x{23FF}\\x{2B50}\\x{2B55}\\x{FE0F}\\x{200D}\\x{20E3}\\x{E0020}-\\x{E007F}\\x{1F900}-\\x{1F9FF}\\x{1FA00}-\\x{1FA6F}\\x{1FA70}-\\x{1FAFF}]+")

    /** Any bracketed tag left after markdown links have been resolved. */
    internal val BRACKETED_TAG = Regex("\\[([^\\[\\]]*)]")

    /** A bracketed tag occupying the very first characters of the text. */
    private val LEADING_INSTRUCTION = Regex("^\\[([^\\[\\]]*)] ?")

    /** A steering instruction is free-form English prose, so only letters and light punctuation. */
    private val INSTRUCTION_CONTENT = Regex("[A-Za-z][A-Za-z ,'-]*")

    /**
     * @param retainSteeringInstructions keep bracketed delivery instructions, which only
     *   `inworld-tts-2` understands. Other models read them aloud, so they are stripped there.
     */
    fun process(script: String, retainSteeringInstructions: Boolean = false): String {
        var result = script

        // 1. Convert **word** → *word*
        result = DOUBLE_ASTERISKS.replace(result, "*$1*")

        // 2. Strip markdown headers
        result = MARKDOWN_HEADERS.replace(result, "")

        // 3. Strip markdown bullet prefixes (keep the text)
        result = MARKDOWN_BULLETS.replace(result, "")

        // 4. Convert markdown links [text](url) → text
        result = MARKDOWN_LINKS.replace(result, "$1")

        // 5. Strip emojis
        result = EMOJIS.replace(result, "")

        // 6. Resolve bracketed tags: sounds always, instructions only where they are understood
        result = BRACKETED_TAG.replace(result) { match ->
            val tag = match.groupValues[1].trim()
            val sound = normalizeSoundName(tag)
            when {
                sound != null -> "[$sound]"
                retainSteeringInstructions && isSteeringInstruction(tag) -> {
                    if (flattensDelivery(tag)) {
                        log.warn("Dropped delivery direction [{}]: it flattens or distorts the read", tag)
                        ""
                    } else {
                        match.value
                    }
                }
                else -> ""
            }
        }

        // Clean up any resulting double spaces or blank lines
        result = result.replace(Regex(" {2,}"), " ")
        result = result.replace(Regex("\\n{3,}"), "\n\n")
        result = result.trim()

        return result
    }

    /** Returns the documented spelling of a sound name, or null when the tag is not a sound. */
    fun normalizeSoundName(tag: String): String? =
        tag.trim().lowercase().replace('_', ' ').takeIf { it in SOUND_TAGS }

    /**
     * True when a steering instruction asks for a flattened or distorted delivery, so it should be
     * dropped instead of forwarded. Matches on whole words, so `[in a deadpan tone]` is caught too.
     */
    fun flattensDelivery(tag: String): Boolean =
        tag.split(INSTRUCTION_WORDS).any { it.isNotEmpty() && it.lowercase() in FLATTENING_INSTRUCTION_WORDS }

    /** True for a bracketed tag that Inworld would treat as a delivery instruction, including `reset`. */
    fun isSteeringInstruction(tag: String): Boolean {
        val content = tag.trim()
        return normalizeSoundName(content) == null && INSTRUCTION_CONTENT.matches(content)
    }

    /**
     * Removes a delivery instruction from the very start of [text], leaving a leading sound tag such
     * as `[laugh]` in place. Applied to the first synthesis request of a script, which has no
     * `synthesisContext` to anchor its delivery: the engine over-commits to an unanchored cue, so a
     * mood like `[with quiet awe]` turns the cold open into a hushed bedtime story.
     */
    fun stripLeadingInstruction(text: String): String {
        val trimmed = text.trimStart()
        val match = LEADING_INSTRUCTION.find(trimmed) ?: return text
        return if (isSteeringInstruction(match.groupValues[1])) trimmed.removeRange(match.range) else text
    }
}
