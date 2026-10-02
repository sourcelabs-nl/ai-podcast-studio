package com.aisummarypodcast.publishing

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

fun OAuthCallbackOutcome.toResponse(pages: OAuthProviderPages): ResponseEntity<String> = when (this) {
    OAuthCallbackOutcome.Connected -> htmlResponse(HttpStatus.OK, pages.title, pages.connectedMessage)
    is OAuthCallbackOutcome.ProviderRefused ->
        htmlResponse(HttpStatus.OK, pages.title, "${pages.title} authorization failed: $error")
    OAuthCallbackOutcome.ExchangeFailed -> htmlResponse(HttpStatus.OK, pages.title, pages.failedMessage)
    OAuthCallbackOutcome.InvalidState -> htmlResponse(
        HttpStatus.BAD_REQUEST, "Authorization Error", "Invalid or tampered authorization request."
    )
    OAuthCallbackOutcome.Expired -> htmlResponse(
        HttpStatus.BAD_REQUEST, "Authorization Expired", "The authorization request has expired. Please try again."
    )
}

private fun htmlResponse(status: HttpStatus, title: String, message: String): ResponseEntity<String> =
    ResponseEntity.status(status)
        .contentType(MediaType.TEXT_HTML)
        .body(OAuthCallbackPage.render(title, message))
