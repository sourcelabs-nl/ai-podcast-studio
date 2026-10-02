package com.aisummarypodcast.publishing

import java.net.URLEncoder

/** Builds an OAuth authorize URL from the endpoint and its query parameters. */
fun buildAuthorizationUrl(endpoint: String, params: Map<String, String>): String {
    val queryString = params.entries.joinToString("&") { (k, v) ->
        "$k=${URLEncoder.encode(v, Charsets.UTF_8)}"
    }
    return "$endpoint?$queryString"
}
