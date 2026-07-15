package com.aisummarypodcast.tts

/**
 * Raised for transient Inworld server-side failures (HTTP 5xx). These are retryable: a brief upstream
 * outage should self-heal on a subsequent attempt rather than failing the whole TTS run.
 */
class InworldTransientException(message: String) : RuntimeException(message)
