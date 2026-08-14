package com.aisummarypodcast.tts

/** A previewed script, which belongs to no episode and so is carried in the request body. */
data class PreviewAudioRequest(val scriptText: String)

data class PreviewAudioEstimateResponse(val characters: Int, val costCents: Int?)
