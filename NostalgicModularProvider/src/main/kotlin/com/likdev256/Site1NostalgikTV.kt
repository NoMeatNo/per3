package com.likdev256

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Site 1: NostalgikTV (nostalgiktv.org)
 * Handles old/nostalgic Persian content: series, movies, cartoons, documentaries, teletheatre.
 */
class Site1NostalgikTV(override val api: MainAPI) : SiteHandler {
    override val siteUrl = "https://nostalgiktv.org"
    override val siteName = "NostalgikTV"
    
    override val mainPages = listOf(
        "$siteUrl/serial-irani" to "🇮🇷 Iranian Series - $siteName",
        "$siteUrl/film-irani" to "🎬 Iranian Movies - $siteName",
        "$siteUrl/serial-khareji" to "🌍 Foreign Series - $siteName",
        "$siteUrl/film-khareji" to "🎥 Foreign Movies - $siteName",
        "$siteUrl/nostalgia-cartoon" to "🎨 Cartoons - $siteName",
        "$siteUrl/mostanad" to "📹 Documentary - $siteName",
        "$siteUrl/teletheatre" to "🎭 Teletheatre - $siteName",
    )
    
    override fun getHomeSelector(url: String): String = "article.item, div.item"
    
    override fun parseHomeItem(element: Element): SearchResponse? {
        val titleElement = element.selectFirst("h2 a, h3 a, .title a")
        val title = titleElement?.text()?.trim() ?: return null
        val href = titleElement.attr("href")?.let { fixUrl(it) } ?: return null
        val posterUrl = element.selectFirst("img")?.attr("src")?.let { fixUrlNull(it) }
        
        // Determine type based on URL
        val type = when {
            href.contains("/film-") -> TvType.Movie
            href.contains("/episode/") -> TvType.TvSeries
            else -> TvType.TvSeries
        }
        
        return with(api) {
            newMovieSearchResponse(title, href, type) {
                this.posterUrl = posterUrl
            }
        }
    }
    
    override suspend fun search(query: String): List<SearchResponse> {
        return try {
            app.get("$siteUrl/?s=${query.replace(" ", "+")}")
                .document.select("article.item, div.result-item, div.search-item")
                .mapNotNull { parseSearchItem(it) }
        } catch (e: Exception) { emptyList() }
    }
    
    private fun parseSearchItem(element: Element): SearchResponse? {
        val titleElement = element.selectFirst("h2 a, h3 a, .title a, a.title")
        val title = titleElement?.text()?.trim() ?: return null
        val href = fixUrl(titleElement.attr("href"))
        val posterUrl = fixUrlNull(element.selectFirst("img")?.attr("src"))
        
        val type = when {
            href.contains("/film-") -> TvType.Movie
            else -> TvType.TvSeries
        }
        
        return with(api) {
            newMovieSearchResponse(title, href, type) {
                this.posterUrl = posterUrl
            }
        }
    }
    
    override suspend fun load(url: String, document: Document): LoadResponse? {
        val isEpisode = url.contains("/episode/")
        val isSeries = url.contains("/serial-") && !isEpisode
        val isMovie = url.contains("/film-") || url.contains("/cartoon") || url.contains("/mostanad") || url.contains("/teletheatre")
        
        return with(api) {
            when {
                isSeries -> {
                    // Series page - extract episode list
                    val title = document.selectFirst("h1, h2.entry-title, .entry-title")?.text()?.trim() ?: return null
                    val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
                    val plot = document.selectFirst("div.entry-content p, .description")?.text()?.trim()
                    
                    val episodes = mutableListOf<Episode>()
                    
                    // Parse episode links from the page
                    document.select("a[href*='/episode/']").forEachIndexed { index, epElement ->
                        val epTitle = epElement.text()?.trim() ?: "Episode ${index + 1}"
                        val epLink = fixUrl(epElement.attr("href"))
                        
                        // Try to extract episode number from title
                        val epNumMatch = Regex("episode[\\s-]*(\\d+)", RegexOption.IGNORE_CASE).find(epTitle)
                        val epNumber = epNumMatch?.groupValues?.get(1)?.toIntOrNull() ?: (index + 1)
                        
                        episodes.add(newEpisode(epLink) {
                            name = epTitle
                            episode = epNumber
                        })
                    }
                    
                    // Remove duplicates and sort by episode number
                    val uniqueEpisodes = episodes.distinctBy { it.data }.sortedBy { it.episode }
                    
                    newTvSeriesLoadResponse(title, url, TvType.TvSeries, uniqueEpisodes) {
                        this.posterUrl = poster
                        this.plot = plot
                    }
                }
                isEpisode -> {
                    // Episode page - treat as movie for direct playback
                    val title = document.selectFirst("h1, h2.entry-title, .entry-title")?.text()?.trim() ?: return null
                    val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
                    val plot = document.selectFirst("div.entry-content p, .description")?.text()?.trim()
                    
                    newMovieLoadResponse(title, url, TvType.Movie, url) {
                        this.posterUrl = poster
                        this.plot = plot
                    }
                }
                else -> {
                    // Movie/Cartoon/Documentary/Teletheatre page
                    val title = document.selectFirst("h1, h2.entry-title, .entry-title")?.text()?.trim() ?: return null
                    val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
                    val plot = document.selectFirst("div.entry-content p, .description")?.text()?.trim()
                    
                    // Check if this is actually a series with episodes
                    val episodeLinks = document.select("a[href*='/episode/']")
                    if (episodeLinks.isNotEmpty()) {
                        val episodes = episodeLinks.mapIndexedNotNull { index, epElement ->
                            val epTitle = epElement.text()?.trim() ?: "Episode ${index + 1}"
                            val epLink = fixUrl(epElement.attr("href"))
                            val epNumMatch = Regex("episode[\\s-]*(\\d+)", RegexOption.IGNORE_CASE).find(epTitle)
                            val epNumber = epNumMatch?.groupValues?.get(1)?.toIntOrNull() ?: (index + 1)
                            
                            newEpisode(epLink) {
                                name = epTitle
                                episode = epNumber
                            }
                        }.distinctBy { it.data }.sortedBy { it.episode }
                        
                        newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                            this.posterUrl = poster
                            this.plot = plot
                        }
                    } else {
                        newMovieLoadResponse(title, url, TvType.Movie, url) {
                            this.posterUrl = poster
                            this.plot = plot
                        }
                    }
                }
            }
        }
    }
    
    override suspend fun loadLinks(
        data: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val document = app.get(data).document
            var foundAny = false
            
            // Method 1: Extract from JW Player setup script
            document.select("script").forEach { scriptElement ->
                val scriptContent = scriptElement.html()
                if (scriptContent.contains("jwplayer") && scriptContent.contains("file:")) {
                    // Extract file URL from jwplayer setup
                    val filePattern = """file:\s*["']([^"']+)["']""".toRegex()
                    val matchResult = filePattern.find(scriptContent)
                    if (matchResult != null) {
                        val videoUrl = matchResult.groupValues[1]
                        if (videoUrl.isNotBlank() && (videoUrl.endsWith(".mp4") || videoUrl.contains(".mp4"))) {
                            callback.invoke(
                                newExtractorLink(
                                    source = siteName,
                                    name = siteName,
                                    url = videoUrl
                                ).apply {
                                    this.quality = Qualities.Unknown.value
                                    this.referer = data
                                    this.headers = mapOf("Referer" to data)
                                }
                            )
                            foundAny = true
                        }
                    }
                }
            }
            
            // Method 2: Extract from Download button
            val downloadButton = document.selectFirst("a.btn.btn-1[href*='.mp4'], a[title*='Download'][href*='.mp4'], a.download-btn[href*='.mp4']")
            if (downloadButton != null) {
                val downloadUrl = downloadButton.attr("href")
                if (downloadUrl.isNotBlank()) {
                    callback.invoke(
                        newExtractorLink(
                            source = siteName,
                            name = "$siteName Download",
                            url = downloadUrl
                        ).apply {
                            this.quality = Qualities.Unknown.value
                            this.referer = data
                            this.headers = mapOf("Referer" to data)
                        }
                    )
                    foundAny = true
                }
            }
            
            // Method 3: Check for direct video source tags
            val videoSource = document.selectFirst("video source[src*='.mp4'], video[src*='.mp4']")
            if (videoSource != null) {
                val videoUrl = videoSource.attr("src").ifEmpty { videoSource.attr("data-src") }
                if (videoUrl.isNotBlank()) {
                    callback.invoke(
                        newExtractorLink(
                            source = siteName,
                            name = "$siteName Direct",
                            url = videoUrl
                        ).apply {
                            this.quality = Qualities.Unknown.value
                            this.referer = data
                            this.headers = mapOf("Referer" to data)
                        }
                    )
                    foundAny = true
                }
            }
            
            foundAny
        } catch (e: Exception) {
            false
        }
    }
    
    private fun fixUrl(url: String): String {
        return if (url.startsWith("http")) url else "$siteUrl$url"
    }
    
    private fun fixUrlNull(url: String?): String? {
        return url?.let { if (it.startsWith("http")) it else "$siteUrl$it" }
    }
}
