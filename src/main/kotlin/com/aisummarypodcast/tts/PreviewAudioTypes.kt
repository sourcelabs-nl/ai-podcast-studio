package com.aisummarypodcast.tts

/** What synthesising a script would bill: the characters actually sent, and their cost. */
data class PreviewAudioEstimate(val characters: Int, val costCents: Int?)
