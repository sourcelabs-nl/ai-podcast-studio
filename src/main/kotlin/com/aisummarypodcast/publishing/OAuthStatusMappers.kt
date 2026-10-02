package com.aisummarypodcast.publishing

import org.springframework.http.ResponseEntity

/** A disconnected account answers with the plain connection status, a connected one adds the quota. */
fun SoundCloudConnectionStatus.toResponse(): Any =
    if (!connection.connected) {
        connection
    } else {
        mapOf(
            "connected" to connection.connected,
            "scopes" to connection.scopes,
            "connectedAt" to connection.connectedAt,
            "quota" to quota?.let {
                mapOf(
                    "unlimitedUploadQuota" to it.unlimitedUploadQuota,
                    "uploadSecondsUsed" to it.uploadSecondsUsed,
                    "uploadSecondsLeft" to it.uploadSecondsLeft
                )
            }
        )
    }

fun integrationNotConfigured(integration: String): ResponseEntity<Any> =
    ResponseEntity.status(503).body(mapOf("error" to "$integration integration is not configured"))
