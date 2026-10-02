package com.aisummarypodcast.publishing

import org.springframework.web.util.HtmlUtils

/** The HTML page shown in the browser after an OAuth redirect. Both arguments are escaped. */
object OAuthCallbackPage {

    fun render(title: String, message: String): String = """
        <!DOCTYPE html>
        <html><head><title>${HtmlUtils.htmlEscape(title)}</title></head>
        <body style="font-family:system-ui;display:flex;justify-content:center;align-items:center;height:100vh;margin:0">
        <p style="font-size:1.2em">${HtmlUtils.htmlEscape(message)}</p>
        </body></html>
    """.trimIndent()
}
