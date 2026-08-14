package com.aisummarypodcast.tts

fun PreviewAudioEstimate.toResponse() = PreviewAudioEstimateResponse(
    characters = characters,
    costCents = costCents
)
