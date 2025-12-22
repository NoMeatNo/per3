package com.likdev256

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Site 1: DiyGuide (diycraftsguide.com)
 * Handles movies, TV series, and Live TV from DiyGuide.
 */
class Site1DiyGuide(override val api: MainAPI) : SiteHandler {
    override val siteUrl = "https://www.diycraftsguide.com"
    override val siteName = "DiyGuide"
    
    override val mainPages = listOf(
        "$siteUrl/movies.html" to "Movies - $siteName",
        "$siteUrl/tv-series.html" to "Series - $siteName",
        "$siteUrl/live-tv.html" to "Live TV - $siteName",
    )
    
    override fun getHomeSelector(url: String): String {
        return when {
            url.contains("live-tv") -> "figure.figure"
            else -> "div.col-md-2.col-sm-3.col-xs-6"
        }
    }
    
    override fun parseHomeItem(element: Element): SearchResponse? {
        // Check if this is a Live TV figure element
        if (element.tagName() == "figure" || element.hasClass("figure")) {
            return parseLiveItem(element)
        }
        
        // Standard movie/series item
        val title = element.selectFirst("div.movie-title h3 a")?.text()?.trim() ?: return null
        val href = element.selectFirst("div.movie-title h3 a")?.attr("href")?.let { fixUrl(it) } ?: return null
        val posterUrl = element.selectFirst("div.latest-movie-img-container")?.attr("data-src")?.trim()?.let { fixUrlNull(it) }
        return with(api) {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
    }
    
    fun parseLiveItem(element: Element): SearchResponse? {
        val title = element.selectFirst("figcaption.figure-caption")?.text()?.trim() ?: return null
        val href = element.selectFirst("a")?.attr("href")?.let { fixUrl(it) } ?: return null
        // Use data-src for lazy-loaded images, fallback to src
        val posterUrl = element.selectFirst("img")?.let { img ->
            val dataSrc = img.attr("data-src")
            if (dataSrc.isNotBlank()) fixUrlNull(dataSrc) else fixUrlNull(img.attr("src"))
        }
        
        return with(api) {
            newMovieSearchResponse(title, href, TvType.Live) {
                this.posterUrl = posterUrl
            }
        }
    }
    
    override suspend fun search(query: String): List<SearchResponse> {
        return try {
            app.get("$siteUrl/search?q=${query.replace(" ", "+")}")
                .document.select("div.col-md-2.col-sm-3.col-xs-6")
                .mapNotNull { parseHomeItem(it) }
        } catch (e: Exception) { emptyList() }
    }
    
    override suspend fun load(url: String, document: Document): LoadResponse? {
        // Handle Live TV channel page
        if (url.contains("/live-tv/") && !url.endsWith("live-tv.html") && !url.contains("/category/")) {
            val title = document.selectFirst("h1")?.text()?.trim() 
                ?: document.selectFirst("title")?.text()?.substringBefore("-")?.trim()
                ?: return null
            val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
            
            return with(api) {
                newMovieLoadResponse(title, url, TvType.Live, url) {
                    this.posterUrl = poster
                }
            }
        }
        
        // Standard movie/series load
        val title = document.selectFirst("div.col-sm-9 p.m-t-10 strong")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("video#play")?.attr("poster"))
        val isTvSeries = document.select(".col-md-12.col-sm-12:has(div.owl-carousel)").isNotEmpty()

        return with(api) {
            if (isTvSeries) {
                val episodes = mutableListOf<Episode>()
                val rows = document.select(".row")
                var seasonNumber = 0
                
                rows.forEach { row ->
                    if (row.select(".movie-heading").isNotEmpty()) {
                        val seasonText = row.select(".movie-heading span").text().trim()
                        seasonNumber = seasonText.removePrefix("Season").trim().toIntOrNull() ?: 0
                    } else if (row.select(".owl-carousel").isNotEmpty() && seasonNumber > 0) {
                        row.select(".item").forEach { item ->
                            val episodeLink = item.select("a").attr("href")
                            val episodeNumberText = item.select(".figure-caption").text().trim()
                            val episodeNumber = episodeNumberText.removePrefix("E").toIntOrNull() ?: 0
                            val episodeName = "Season $seasonNumber Episode $episodeNumber"
                            if (episodeLink.isNotEmpty()) {
                                episodes.add(newEpisode(episodeLink) {
                                    name = episodeName
                                    season = seasonNumber
                                    episode = episodeNumber
                                })
                            }
                        }
                    }
                }

                newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster
                }
            } else {
                newMovieLoadResponse(title, url, TvType.Movie, url) {
                    this.posterUrl = poster
                }
            }
        }
    }
    
    override suspend fun loadLinks(
        data: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var foundLinks = false
        
        // Handle Live TV stream extraction
        if (data.contains("/live-tv/")) {
            try {
                val document = app.get(data).document
                val pageHtml = document.html()
                
                // Helper to extract and add stream link from HTML
                suspend fun tryExtractStream(html: String, serverName: String, referer: String): Boolean {
                    // 1. Extract from videojs sources array in script: sources: [{src: 'url', type: ''}]
                    val sourcesRegex = Regex("""sources:\s*\[\s*\{\s*src:\s*['"]([^'"]+)['"]""")
                    val streamUrl = sourcesRegex.find(html)?.groupValues?.get(1)
                        ?: run {
                            // 2. Fallback: Extract from file: 'url' pattern  
                            val fileRegex = Regex("""file:\s*['"]([^'"]+\.m3u8[^'"]*|[^'"]+\.mp4[^'"]*|[^'"]+\.txt[^'"]*)['"]""")
                            fileRegex.find(html)?.groupValues?.get(1)
                        }
                    
                    if (streamUrl != null) {
                        callback.invoke(
                            newExtractorLink(
                                source = siteName,
                                name = "$siteName - $serverName",
                                url = streamUrl
                            ).apply {
                                this.quality = Qualities.Unknown.value
                                this.referer = referer
                            }
                        )
                        return true
                    }
                    return false
                }
                
                // Try main page first
                if (tryExtractStream(pageHtml, "Live", data)) {
                    foundLinks = true
                }
                
                // Also check for alternative server buttons with ?key= parameter
                val serverButtons = document.select("a[href*='?key='], button[data-key], .btn-group a, .server-list a")
                val seenUrls = mutableSetOf(data)
                
                serverButtons.forEach { btn ->
                    val altUrl = btn.attr("href").let { if (it.startsWith("http")) it else fixUrl(it) }
                    if (altUrl.contains("?key=") && altUrl !in seenUrls) {
                        seenUrls.add(altUrl)
                        val serverLabel = btn.text().trim().ifBlank { "Server ${seenUrls.size}" }
                        
                        try {
                            val altHtml = app.get(altUrl).text
                            if (tryExtractStream(altHtml, serverLabel, altUrl)) {
                                foundLinks = true
                            }
                        } catch (_: Exception) {}
                    }
                }
                
                // Check for YouTube embeds
                document.select("iframe").forEach { iframe ->
                    val src = iframe.attr("src")
                    if (src.contains("youtube.com") || src.contains("youtu.be")) {
                        loadExtractor(src, data, subtitleCallback, callback)
                        foundLinks = true
                    }
                }
                
                return foundLinks
            } catch (_: Exception) {
                return false
            }
        }
        
        // Standard movie/series link extraction
        data.split(",").forEach { url ->
            try {
                val document = app.get(url.trim()).document
                
                // Extract MP4 links from scripts
                val scriptContent = document.selectFirst("script:containsData('video/mp4')")?.data() ?: ""
                val mp4LinkRegex = Regex("""src: '(https?://[^']+\.mp4)'""")
                mp4LinkRegex.findAll(scriptContent).forEach { matchResult ->
                    if (matchResult.groupValues.size > 1) {
                        val mp4Link = matchResult.groupValues[1]
                        if (mp4Link.isNotBlank()) {
                            callback.invoke(
                                newExtractorLink(
                                    source = siteName,
                                    name = siteName,
                                    url = mp4Link
                                ).apply {
                                    this.quality = Qualities.Unknown.value
                                }
                            )
                            foundLinks = true
                        }
                    }
                }
                
                // Extract ok.ru / VK embedded videos from iframes
                document.select("iframe").forEach { iframe ->
                    val iframeSrc = iframe.attr("src")
                    if (iframeSrc.isNotBlank()) {
                        if (iframeSrc.contains("ok.ru") || iframeSrc.contains("vk.com")) {
                            loadExtractor(iframeSrc, siteUrl, subtitleCallback, callback)
                            foundLinks = true
                        }
                    }
                }
                
                // Also check for video-embed-container divs with iframes
                document.select("div.video-embed-container iframe").forEach { iframe ->
                    val iframeSrc = iframe.attr("src")
                    if (iframeSrc.isNotBlank() && (iframeSrc.contains("ok.ru") || iframeSrc.contains("vk.com"))) {
                        loadExtractor(iframeSrc, siteUrl, subtitleCallback, callback)
                        foundLinks = true
                    }
                }
            } catch (_: Exception) {}
        }
        return foundLinks
    }
    
    private fun fixUrl(url: String): String {
        return if (url.startsWith("http")) url else "$siteUrl$url"
    }
    
    private fun fixUrlNull(url: String?): String? {
        return url?.let { if (it.startsWith("http")) it else "$siteUrl$it" }
    }
}