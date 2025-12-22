package com.likdev256

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Site 5: PersianHive (persianhive.com)
 * Handles movies, TV series, and Live TV from PersianHive.
 * Uses Cloudflare bypass.
 */
class Site5PersianHive(override val api: MainAPI) : SiteHandler {
    override val siteUrl = "https://persianhive.com"
    override val siteName = "PersianHive"
    
    // Cloudflare bypass interceptor
    val cfKiller by lazy { CloudflareKiller() }
    
    override val mainPages = listOf(
        "$siteUrl/series-web/" to "Series - $siteName",
        "$siteUrl/movies_films/" to "Movies - $siteName",
        "$siteUrl/live-tv/" to "Live TV - $siteName",
    )
    
    override fun getHomeSelector(url: String): String {
        return when {
            url.contains("live-tv") -> "div.icon-container"
            url.contains("movies") -> "article.post-item"
            else -> "div.pciwgas-post-cat-inner"
        }
    }
    
    override fun parseHomeItem(element: Element): SearchResponse? {
        // This is called for series pages
        val titleElement = element.selectFirst("div.pciwgas-title a, div.pciw-title a")
        val title = titleElement?.text()?.trim() ?: return null
        val hoverLink = element.selectFirst("a.pciwgas-hover, a.pciw-hover")
        val href = (hoverLink?.attr("href") ?: titleElement?.attr("href"))?.let { fixUrl(it) } ?: return null
        val posterUrl = element.selectFirst("img.pciwgas-img, img.pciw-img")?.attr("src")?.let { fixUrlNull(it) }
        
        return with(api) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        }
    }
    
    fun parseMovieItem(element: Element): SearchResponse? {
        val titleElement = element.selectFirst(".post-title h2 a, .entry-title a")
        val title = titleElement?.text()?.trim() ?: return null
        val href = fixUrl(titleElement.attr("href"))
        val posterUrl = fixUrlNull(element.selectFirst(".image_wrapper img, .image_frame img")?.attr("src"))
        
        return with(api) {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
    }
    
    fun parseLiveItem(element: Element): SearchResponse? {
        val textElement = element.selectFirst(".icon-text")
        val title = textElement?.text()?.trim() ?: return null
        val videoId = element.attr("data-video-id")
        
        val imgSrc = element.selectFirst("img.icon, img")?.attr("src")
        val posterUrl = if (imgSrc != null && imgSrc.startsWith("/")) {
            "$siteUrl$imgSrc"
        } else {
            fixUrlNull(imgSrc)
        }
        
        val href = "$siteUrl/live-tv/?channel=$videoId"
        
        return with(api) {
            newMovieSearchResponse(title, href, TvType.Live) {
                this.posterUrl = posterUrl
            }
        }
    }
    
    override suspend fun search(query: String): List<SearchResponse> {
        return try {
            app.get("$siteUrl/?s=${query.replace(" ", "+")}", interceptor = cfKiller)
                .document.select("div.pciwgas-post-cat-inner")
                .mapNotNull { parseHomeItem(it) }
        } catch (e: Exception) { emptyList() }
    }
    
    override suspend fun load(url: String, document: Document): LoadResponse? {
        // Handle Live TV channel with video ID
        if (url.contains("live-tv") && url.contains("channel=")) {
            val channelName = "Live Channel"
            return with(api) {
                newMovieLoadResponse(channelName, url, TvType.Live, url) {
                    this.posterUrl = null
                }
            }
        }
        
        val title = document.selectFirst("h1.page-title")?.text()?.trim() 
            ?: document.selectFirst("h1.entry-title, h1.pciwgas-title, h1")?.text()?.trim() 
            ?: url.substringAfter("/category/").substringBefore("/").replace("-", " ").replaceFirstChar { it.uppercaseChar() }
        val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
        val description = document.selectFirst("div.entry-content p, div.pciwgas-desc p")?.text()?.trim()
        
        // Check if this is a category page (series episodes list)
        val categoryEpisodes = document.select("article.post-item, article.post")
        
        return with(api) {
            if (categoryEpisodes.isNotEmpty()) {
                val episodes = categoryEpisodes.mapNotNull {
                    val epTitleElement = it.selectFirst("h2.entry-title a") 
                        ?: it.selectFirst(".post-title h2 a")
                        ?: it.selectFirst(".image_wrapper a")
                    val epTitle = epTitleElement?.text()?.trim() ?: return@mapNotNull null
                    val epUrl = fixUrl(epTitleElement.attr("href"))
                    val epPoster = fixUrlNull(it.selectFirst(".image_wrapper img")?.attr("src"))
                    
                    val epNumber = Regex("""Episode\s*(\d+)""", RegexOption.IGNORE_CASE).find(epTitle)?.groupValues?.get(1)?.toIntOrNull()
                        ?: Regex("""\d+$""").find(epTitle)?.value?.toIntOrNull()
                    
                    newEpisode(epUrl) {
                        name = epTitle
                        episode = epNumber
                        posterUrl = epPoster
                    }
                }
                
                if (episodes.isNotEmpty()) {
                    return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                        this.posterUrl = poster ?: episodes.firstOrNull()?.posterUrl
                        this.plot = description
                    }
                }
            }
            
            // Single movie/episode page
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
            }
        }
    }
    
    override suspend fun loadLinks(
        data: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            var foundLinks = false
            
            // Handle Live TV channels via AJAX
            if (data.contains("live-tv") && data.contains("channel=")) {
                val channelId = data.substringAfter("channel=").substringBefore("&")
                if (channelId.isNotBlank()) {
                    // Fetch the live-tv page to get the nonce
                    val liveTvDoc = app.get("$siteUrl/live-tv/", interceptor = cfKiller).document
                    val nonce = liveTvDoc.selectFirst("div.custom-icon-grid")?.attr("data-nonce") ?: ""
                    
                    if (nonce.isNotBlank()) {
                        // Make AJAX POST request to get video URL
                        val ajaxResponse = app.post(
                            "$siteUrl/wp-admin/admin-ajax.php",
                            data = mapOf(
                                "action" to "custom_icon_grid_get_video_url",
                                "video_id" to channelId,
                                "nonce" to nonce
                            ),
                            headers = mapOf("Referer" to "$siteUrl/live-tv/"),
                            interceptor = cfKiller
                        ).text
                        
                        // Extract URL from JSON response
                        val urlRegex = Regex(""""url"\s*:\s*"([^"]+)"""")
                        urlRegex.find(ajaxResponse)?.groupValues?.get(1)?.let { videoUrl ->
                            val cleanUrl = videoUrl.replace("\\/", "/")
                            callback.invoke(
                                newExtractorLink(
                                    source = siteName,
                                    name = "$siteName - Live",
                                    url = cleanUrl
                                ).apply {
                                    this.quality = Qualities.Unknown.value
                                    this.referer = "$siteUrl/live-tv/"
                                }
                            )
                            foundLinks = true
                        }
                    }
                }
                return foundLinks
            }
            
            // Use Cloudflare bypass interceptor for regular pages
            val document = app.get(data, interceptor = cfKiller).document
            
            // Check for iframes
            document.select("iframe").forEach { iframe ->
                val src = iframe.attr("src")
                if (src.isNotBlank()) {
                    loadExtractor(src, data, subtitleCallback, callback)
                    foundLinks = true
                }
            }

            // Check for PlayerJS source in scripts
            document.select("script").forEach { script ->
                val scriptContent = script.data()
                if (scriptContent.contains("file:")) {
                    val fileRegex = Regex("""file\s*:\s*["']([^"']+)["']""")
                    fileRegex.findAll(scriptContent).forEach { match ->
                        val videoUrl = match.groupValues[1]
                        if (videoUrl.endsWith(".m3u8") || videoUrl.endsWith(".mp4") || videoUrl.contains("playlist")) {
                            callback.invoke(
                                newExtractorLink(
                                    source = siteName,
                                    name = "$siteName - Stream",
                                    url = videoUrl
                                ).apply {
                                    this.quality = Qualities.Unknown.value
                                    this.referer = data
                                }
                            )
                            foundLinks = true
                        }
                    }
                }
            }
            foundLinks
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
