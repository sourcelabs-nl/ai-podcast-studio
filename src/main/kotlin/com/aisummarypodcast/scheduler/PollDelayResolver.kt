package com.aisummarypodcast.scheduler

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.source.extractSourceHost
import com.aisummarypodcast.store.Source
import org.springframework.stereotype.Component

@Component
class PollDelayResolver(private val appProperties: AppProperties) {

    fun resolveDelaySeconds(source: Source): Int {
        source.pollDelaySeconds?.let { return it }

        val host = extractSourceHost(source.url)
        if (host != null) {
            appProperties.source.hostOverrides[host]?.let { return it.pollDelaySeconds }
        }

        appProperties.source.pollDelaySeconds[source.type.value]?.let { return it }

        return 0
    }
}
