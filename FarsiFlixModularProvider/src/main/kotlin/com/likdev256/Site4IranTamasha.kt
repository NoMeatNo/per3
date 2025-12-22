package com.likdev256

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Site 4: IranTamasha (irantamasha.com)
 * Handles TV series from IranTamasha.
 */
class Site4IranTamasha(override val api: MainAPI) : SiteHandler {
    override val siteUrl = "https://www.irantamasha.com"
    override val siteName = "IranTamasha"
    
    override val mainPages = listOf(
        "$siteUrl/series/" to "Series - $siteName",
    )
    
    override fun getHomeSelector(url: String): String = "article.post-item"
    
    override fun parseHomeItem(element: Element): SearchResponse? {
        val title = element.selectFirst("h3.entry-title a")?.text()?.trim() ?: return null
        val href = element.selectFirst("h3.entry-title a")?.attr("href")?.let { fixUrl(it) } ?: return null
        val posterUrl = (element.selectFirst("div.blog-pic img")?.attr("src")?.trim() 
            ?: element.selectFirst("div.blog-pic img")?.attr("data-src")?.trim())?.let { fixUrlNull(it) }
        return with(api) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        }
    }
    
    override suspend fun search(query: String): List<SearchResponse> {
        return try {
            app.get("$siteUrl/?s=${query.replace(" ", "+")}")
                .document.select("article.post-item")
                .mapNotNull { parseHomeItem(it) }
        } catch (e: Exception) { emptyList() }
    }
    
    override suspend fun load(url: String, document: Document): LoadResponse? {
        val title = document.selectFirst("h1.entry-title")?.text()?.trim() 
            ?: document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.blog-pic img")?.attr("src") 
            ?: document.selectFirst("meta[property=og:image]")?.attr("content"))
        val plot = document.selectFirst("div.entry-content p")?.text()?.trim()

        // Check if it is a series page (list of episodes)
        val episodeElements = document.select("article.post-item")
        
        return with(api) {
            if (episodeElements.isNotEmpty() && !url.contains("-0") && !url.contains("-1")) {
                // Likely a series page listing episodes
                val episodes = episodeElements.mapNotNull {
                    val epTitle = it.selectFirst("h3.entry-title a")?.text()?.trim() ?: return@mapNotNull null
                    val epUrl = fixUrl(it.selectFirst("h3.entry-title a")?.attr("href") ?: return@mapNotNull null)
                    val epPoster = fixUrlNull(it.selectFirst("img")?.attr("src"))
                    
                    // Try to extract episode number from title (e.g. "Close Friend – 02")
                    val epNumber = Regex("""\d+$""").find(epTitle)?.value?.toIntOrNull() 
                        ?: Regex(""" (\d+)""").findAll(epTitle).lastOrNull()?.value?.trim()?.toIntOrNull() ?: 0

                    newEpisode(epUrl) {
                        name = epTitle
                        episode = epNumber
                        posterUrl = epPoster
                    }
                }.reversed() // Usually listed newest first

                newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster
                    this.plot = plot
                }
            } else {
                // Likely a single episode or movie page
                newMovieLoadResponse(title, url, TvType.Movie, url) {
                    this.posterUrl = poster
                    this.plot = plot
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
            var foundLinks = 0

            // 1. Extract from current page - Look for iframes directly
            document.select("iframe").forEach { iframe ->
                val src = iframe.attr("src")
                if (src.isNotBlank()) {
                    when {
                        src.contains("ok.ru") || src.contains("vk.com") || src.contains("vkvideo.ru") || 
                        src.contains("closeload") || src.contains("youtube") || src.contains("dailymotion") -> {
                            loadExtractor(src, data, subtitleCallback, callback)
                            foundLinks++
                        }
                        src.contains("evp_play") -> {
                            // Handle evp_play locally
                            if (extractEvpLinks(src, data, callback)) foundLinks++
                        }
                    }
                }
            }
            
            // 2. Look for Multi-Links using class "series-item" (Server 1, Server 2, etc.)
            val serverLinks = document.select("a.series-item")
            
            serverLinks.forEach { link ->
                val serverUrl = fixUrl(link.attr("href"))
                if (serverUrl != data) { // Avoid infinite recursion if it's the same page
                    try {
                        val serverDoc = app.get(serverUrl).document
                        serverDoc.select("iframe").forEach { iframe ->
                            val src = iframe.attr("src")
                            if (src.isNotBlank()) {
                                when {
                                    src.contains("goodstream.one") -> {
                                        try {
                                            val embedHtml = app.get(src, headers = mapOf("Referer" to serverUrl)).text
                                            // Extract file URL from JWPlayer sources: [{file:"https://...m3u8?..."}]
                                            val fileRegex = Regex("""file\s*:\s*["']([^"']+)["']""")
                                            fileRegex.find(embedHtml)?.groupValues?.get(1)?.let { m3u8Url ->
                                                callback.invoke(
                                                    newExtractorLink(
                                                        source = "GoodStream",
                                                        name = "GoodStream - HLS",
                                                        url = m3u8Url
                                                    ).apply {
                                                        this.quality = Qualities.Unknown.value
                                                        this.referer = src
                                                    }
                                                )
                                                foundLinks++
                                            }
                                        } catch (_: Exception) {}
                                    }
                                    src.contains("evp_play") -> {
                                        if (extractEvpLinks(src, serverUrl, callback)) foundLinks++
                                    }
                                    else -> {
                                        // Fallback to CloudStream's built-in extractors (handles Dailymotion, VK, etc.)
                                        loadExtractor(src, data, subtitleCallback, callback)
                                        foundLinks++
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            // 3. Look for video elements or scripts
            document.select("video source").forEach { source ->
                val src = source.attr("src")
                if (src.isNotBlank()) {
                    callback.invoke(
                        newExtractorLink(
                            source = siteName,
                            name = siteName,
                            url = src
                        ).apply {
                            this.quality = Qualities.Unknown.value
                        }
                    )
                    foundLinks++
                }
            }

            foundLinks > 0
        } catch (_: Exception) {
            false
        }
    }
    
    private suspend fun extractEvpLinks(
        iframeSrc: String,
        refererUrl: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val fullIframeUrl = fixUrl(iframeSrc)
        
        return try {
            // 1. Fetch the player page with Referer
            val playerDoc = app.get(fullIframeUrl, headers = mapOf("Referer" to refererUrl)).document
            
            // 2. Extract CONFIG data from script - look for encrypted_id which is always present
            val scriptContent = playerDoc.select("script").find { it.data().contains("encrypted_id") }?.data()
            
            if (scriptContent != null) {
                val ajaxUrl = Regex("""ajax_url['""]?\s*:\s*['"]([^'"]+)['"]""").find(scriptContent)?.groupValues?.get(1)?.replace("\\/", "/")
                val encryptedId = Regex("""encrypted_id['""]?\s*:\s*['"]([^'"]+)['"]""").find(scriptContent)?.groupValues?.get(1)
                val nonce = Regex("""nonce['""]?\s*:\s*['"]([^'"]+)['"]""").find(scriptContent)?.groupValues?.get(1)
                
                if (ajaxUrl != null && encryptedId != null && nonce != null) {
                    // 3. Make POST request
                    val postData = mapOf(
                        "action" to "evp_get_video_url",
                        "encrypted_id" to encryptedId,
                        "nonce" to nonce
                    )
                    
                    val jsonResponse = app.post(ajaxUrl, data = postData, headers = mapOf(
                        "Referer" to fullIframeUrl,
                        "X-Requested-With" to "XMLHttpRequest"
                    )).text
                    
                    // 4. Extract URLs from JSON - the API returns {"servers":["url1", "url2", ...]}
                    val serversBlock = Regex(""""servers"\s*:\s*\[(.*?)\]""").find(jsonResponse)?.groupValues?.get(1)
                    
                    if (serversBlock != null) {
                        var found = false
                        var serverIndex = 0
                        val urlMatches = Regex(""""([^"]+)"""").findAll(serversBlock)
                        urlMatches.forEach { match ->
                            val videoUrl = match.groupValues[1].replace("\\/", "/")
                            
                            if (videoUrl.isNotBlank() && (videoUrl.startsWith("http") || videoUrl.contains(".m3u8") || videoUrl.contains(".mp4") || videoUrl.contains(".txt"))) {
                                serverIndex++
                                // Use direct ExtractorLink with isM3u8 flag for HLS streams
                                val isHls = videoUrl.contains(".m3u8") || videoUrl.contains(".txt")
                                callback.invoke(
                                    ExtractorLink(
                                        source = siteName,
                                        name = if (serverIndex == 1) "$siteName - EVP" else "$siteName - Server $serverIndex",
                                        url = videoUrl,
                                        referer = fullIframeUrl,
                                        quality = Qualities.Unknown.value,
                                        isM3u8 = isHls
                                    )
                                )
                                found = true
                            }
                        }
                        return found
                    }
                }
            }
            false
        } catch (_: Exception) {
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
