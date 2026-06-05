package com.aisummarypodcast.podcast

import com.aisummarypodcast.config.AppProperties
import com.aisummarypodcast.store.Episode
import com.aisummarypodcast.store.EpisodeArticleRepository
import com.aisummarypodcast.store.EpisodePublicationRepository
import com.aisummarypodcast.store.EpisodeRepository
import com.aisummarypodcast.store.EpisodeStatus
import com.aisummarypodcast.store.FeedArticle
import com.aisummarypodcast.store.Podcast
import com.aisummarypodcast.store.User
import com.rometools.modules.atom.modules.AtomLinkModuleImpl
import com.rometools.modules.itunes.EntryInformationImpl
import com.rometools.modules.itunes.FeedInformationImpl
import com.rometools.modules.itunes.types.Category
import com.rometools.modules.itunes.types.Duration
import com.rometools.rome.feed.atom.Link
import com.rometools.rome.feed.synd.SyndContentImpl
import com.rometools.rome.feed.synd.SyndEnclosureImpl
import com.rometools.rome.feed.synd.SyndEntryImpl
import com.rometools.rome.feed.synd.SyndFeedImpl
import com.rometools.rome.feed.synd.SyndImageImpl
import com.rometools.rome.feed.rss.Channel
import com.rometools.rome.io.WireFeedOutput
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date

@Component
class FeedGenerator(
    private val episodeRepository: EpisodeRepository,
    private val appProperties: AppProperties,
    private val podcastImageService: PodcastImageService,
    private val episodeSourcesGenerator: EpisodeSourcesGenerator,
    private val publicationRepository: EpisodePublicationRepository,
    private val episodeArticleRepository: EpisodeArticleRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun generate(podcast: Podcast, user: User, baseUrl: String? = null, publicUrl: String? = null, publishedTarget: String? = null): String {
        val effectiveBaseUrl = baseUrl ?: appProperties.feed.baseUrl
        val contentBaseUrl = publicUrl ?: "$effectiveBaseUrl/data/${podcast.id}/"
        val feedTitle = podcast.name

        val feed = SyndFeedImpl().apply {
            feedType = "rss_2.0"
            title = feedTitle
            link = effectiveBaseUrl
            description = appProperties.feed.description
            language = podcast.language
            publishedDate = Date.from(Instant.now())
        }

        // Channel-level content:encoded (HTML description)
        feed.descriptionEx = SyndContentImpl().apply {
            type = "text/html"
            value = "<p>${escapeHtml(appProperties.feed.description)}</p>"
        }

        val imagePath = podcastImageService.get(podcast.id)
        val imageUrl = if (imagePath != null) "${contentBaseUrl}${imagePath.fileName}" else null
        if (imageUrl != null) {
            feed.image = SyndImageImpl().apply {
                url = imageUrl
                title = feedTitle
                link = effectiveBaseUrl
            }
        }

        val feedProps = appProperties.feed
        val itunes = FeedInformationImpl()
        feedProps.ownerName?.let { itunes.ownerName = it }
        feedProps.ownerEmail?.let { itunes.ownerEmailAddress = it }
        feedProps.author?.let { itunes.author = it }
        itunes.explicitNullable = feedProps.explicit
        itunes.type = "episodic"
        itunes.categories = listOf(Category(feedProps.itunesCategory))
        if (imageUrl != null) {
            itunes.imageUri = imageUrl
        }

        // Atom self-link
        val feedUrl = "${effectiveBaseUrl}/users/${user.id}/podcasts/${podcast.id}/feed.xml"
        val atomModule = AtomLinkModuleImpl()
        atomModule.link = Link().apply {
            href = feedUrl
            rel = "self"
            type = "application/rss+xml"
        }

        val modules = feed.modules.toMutableList()
        modules.add(itunes)
        modules.add(atomModule)
        feed.modules = modules

        val allEpisodes = episodeRepository.findByPodcastIdAndStatusOrderByGeneratedAtDescIdDesc(podcast.id, EpisodeStatus.GENERATED)
        val episodes = if (publishedTarget != null) {
            val publishedEpisodeIds = publicationRepository.findPublishedByPodcastIdAndTarget(podcast.id, publishedTarget)
                .map { it.episodeId }.toSet()
            allEpisodes.filter { it.id in publishedEpisodeIds }
        } else {
            allEpisodes
        }

        val episodeIds = episodes.map { it.id!! }
        val articlesByEpisode = episodeArticleRepository.findArticlesByEpisodeIds(episodeIds)

        feed.entries = episodes.mapNotNull { episode ->
            val audioPath = episode.audioFilePath ?: return@mapNotNull null
            val audioFileName = Path.of(audioPath).fileName
            val slug = episodeSourcesGenerator.deriveSlug(episode)
            val sourcesUrl = "${contentBaseUrl}episodes/$slug-sources.html"
            val articles = articlesByEpisode[episode.id] ?: emptyList()

            SyndEntryImpl().apply {
                val generatedInstant = Instant.parse(episode.generatedAt)
                title = "$feedTitle - ${generatedInstant.atOffset(ZoneOffset.UTC).toLocalDate()}"
                link = sourcesUrl
                publishedDate = Date.from(generatedInstant)

                description = SyndContentImpl().apply {
                    type = "text/plain"
                    value = buildPlainDescription(episode)
                }

                // content:encoded with HTML including clickable source links
                contents = listOf(SyndContentImpl().apply {
                    type = "html"
                    value = buildHtmlDescription(episode, articles, sourcesUrl, feedProps.ownerEmail)
                })

                enclosures = listOf(SyndEnclosureImpl().apply {
                    url = "${contentBaseUrl}episodes/$audioFileName"
                    this.type = "audio/mpeg"
                    length = try {
                        Files.size(Path.of(audioPath))
                    } catch (_: Exception) {
                        0L
                    }
                })

                // Per-episode iTunes metadata
                val entryItunes = EntryInformationImpl()
                entryItunes.explicitNullable = feedProps.explicit
                entryItunes.episodeType = "full"
                feedProps.author?.let { entryItunes.author = it }
                episode.durationSeconds?.let { secs ->
                    entryItunes.duration = Duration(secs * 1000L)
                }
                val entryModules = this.modules.toMutableList()
                entryModules.add(entryItunes)
                this.modules = entryModules
            }
        }

        val wireFeed = feed.createWireFeed() as Channel
        wireFeed.ttl = 60
        wireFeed.lastBuildDate = Date.from(Instant.now())

        val writer = StringWriter()
        WireFeedOutput().output(wireFeed, writer)
        val xml = writer.toString()

        log.info("Generated RSS feed for podcast {} with {} episodes", podcast.id, episodes.size)
        return xml
    }

    private fun buildPlainDescription(episode: Episode): String {
        return episode.showNotes ?: episode.recap ?: (episode.scriptText.take(500) + "...")
    }

    private fun buildHtmlDescription(episode: Episode, articles: List<FeedArticle>, sourcesUrl: String, ownerEmail: String?): String {
        val recap = episode.showNotes ?: episode.recap ?: (episode.scriptText.take(500) + "...")
        val hasTopics = articles.any { it.topicOrder != null }
        return buildString {
            // Show notes as paragraphs
            for (paragraph in recap.split("\n\n")) {
                val trimmed = paragraph.trim()
                if (trimmed.isNotEmpty()) {
                    append("<p>${escapeHtml(trimmed)}</p>")
                }
            }
            // Topic names only — the full per-article list lives on the linked sources page
            if (hasTopics) {
                val topics = articles.filter { it.topicOrder != null }
                    .map { it.topic ?: "Other" }
                    .distinct()
                if (topics.isNotEmpty()) {
                    append("<h2>Topics Covered</h2><ul>")
                    for (topic in topics) {
                        append("<li>${escapeHtml(topic)}</li>")
                    }
                    append("</ul>")
                }
            }
            append("<p>For the full list of sources that inspired this episode, <a href=\"${escapeHtml(sourcesUrl)}\">view all sources and show notes</a>.</p>")
            if (ownerEmail != null) {
                append("<hr/><p><em>Tips, comments, or feedback? Mail us at <a href=\"mailto:${escapeHtml(ownerEmail)}\">${escapeHtml(ownerEmail)}</a></em></p>")
            }
        }
    }

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
