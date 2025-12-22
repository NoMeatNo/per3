package com.likdev256

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Site 4: IranTamasha (https://www.irantamasha.com)
 * Extension functions for FarsiFlixMegaProvider
 */

const val SITE4_URL = "https://www.irantamasha.com"
const val SITE4_NAME = "IranTamasha"

fun Element.toSite4SearchResult(): SearchResponse? {
    val title = this.selectFirst("h3.entry-title a")?.text()?.trim() ?: return null
    val href = this.selectFirst("h3.entry-title a")?.attr("href") ?: return null
    val posterUrl = this.selectFirst("div.blog-pic img")?.attr("src")?.trim() ?:
                     this.selectFirst("div.blog-pic img")?.attr("data-src")?.trim()
    return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
        this.posterUrl = posterUrl
    }
}

suspend fun FarsiFlixMegaProvider.loadSite4(url: String, document: Document): LoadResponse? {
    val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: 
                document.selectFirst("h1")?.text()?.trim() ?: return null
    val poster = fixUrlNull(document.selectFirst("div.blog-pic img")?.attr("src") ?: 
                          document.selectFirst("meta[property=og:image]")?.attr("content"))
    val plot = document.selectFirst("div.entry-content p")?.text()?.trim()

    val episodeElements = document.select("article.post-item")
    
    return if (episodeElements.isNotEmpty() && !url.contains("-0") && !url.contains("-1")) {
        val episodes = episodeElements.mapNotNull {
            val epTitle = it.selectFirst("h3.entry-title a")?.text()?.trim() ?: return@mapNotNull null
            val epUrl = fixUrl(it.selectFirst("h3.entry-title a")?.attr("href") ?: return@mapNotNull null)
            val epPoster = fixUrlNull(it.selectFirst("img")?.attr("src"))
            val epNumber = Regex("""\d+$""").find(epTitle)?.value?.toIntOrNull() ?: 
                           Regex(""" (\d+)""").findAll(epTitle).lastOrNull()?.value?.trim()?.toIntOrNull() ?: 0

            newEpisode(epUrl) {
                name = epTitle
                episode = epNumber
                posterUrl = epPoster
            }
        }.reversed()

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

suspend fun FarsiFlixMegaProvider.loadLinksSite4(
    data: String,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    return try {
        val document = app.get(data).document
        var foundLinks = 0

        // Look for iframes
        document.select("iframe").forEach { iframe ->
            val src = iframe.attr("src")
            if (src.contains("ok.ru") || src.contains("vk.com") || src.contains("vkvideo.ru") || src.contains("closeload") || src.contains("youtube")) {
                loadExtractor(src, data, subtitleCallback, callback)
                foundLinks++
            }
        }
        
        // Look for Multi-Links using class "series-item"
        val serverLinks = document.select("a.series-item")
        
        serverLinks.forEach { link ->
            val serverUrl = fixUrl(link.attr("href"))
            if (serverUrl != data) {
                try {
                    val serverDoc = app.get(serverUrl).document
                    serverDoc.select("iframe").forEach { iframe ->
                        val src = iframe.attr("src")
                        if (src.isNotBlank()) {
                            if (src.contains("goodstream.one")) {
                                try {
                                    val embedHtml = app.get(src, headers = mapOf("Referer" to serverUrl)).text
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
                            } else {
                                loadExtractor(src, data, subtitleCallback, callback)
                                foundLinks++
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // Look for video elements
        document.select("video source").forEach { source ->
            val src = source.attr("src")
            if (src.isNotBlank()) {
                callback.invoke(
                    newExtractorLink(
                        source = SITE4_NAME,
                        name = SITE4_NAME,
                        url = src
                    ).apply {
                        this.quality = Qualities.Unknown.value
                    }
                )
                foundLinks++
            }
        }

        // Look for evp_play iframe
        document.select("iframe[src*='evp_play']").forEach { iframe ->
            val src = iframe.attr("src")
            val fullIframeUrl = fixUrl(src)
            
            try {
                val playerDoc = app.get(fullIframeUrl, headers = mapOf("Referer" to data)).document
                val scriptContent = playerDoc.select("script").find { it.data().contains("const CONFIG =") }?.data()
                
                if (scriptContent != null) {
                    val ajaxUrl = Regex("""ajax_url['"]?\s*:\s*['"]([^'"]+)['"]""").find(scriptContent)?.groupValues?.get(1)?.replace("\\/", "/")
                    val encryptedId = Regex("""encrypted_id['"]?\s*:\s*['"]([^'"]+)['"]""").find(scriptContent)?.groupValues?.get(1)
                    val nonce = Regex("""nonce['"]?\s*:\s*['"]([^'"]+)['"]""").find(scriptContent)?.groupValues?.get(1)
                    
                    if (ajaxUrl != null && encryptedId != null && nonce != null) {
                        val postData = mapOf(
                            "action" to "evp_get_video_url",
                            "encrypted_id" to encryptedId,
                            "nonce" to nonce
                        )
                        
                        val jsonResponse = app.post(ajaxUrl, data = postData, headers = mapOf("Referer" to fullIframeUrl)).text
                        val serversBlock = Regex(""""servers"\s*:\s*\[(.*?)\]""").find(jsonResponse)?.groupValues?.get(1)
                        
                        if (serversBlock != null) {
                            val urlMatches = Regex(""""([^"]+)"""").findAll(serversBlock)
                            urlMatches.forEach { match ->
                                val videoUrl = match.groupValues[1].replace("\\/", "/")
                                
                                callback.invoke(
                                    newExtractorLink(
                                        source = SITE4_NAME,
                                        name = "$SITE4_NAME - EVP",
                                        url = videoUrl
                                    ).apply {
                                        this.quality = Qualities.Unknown.value
                                        this.referer = fullIframeUrl
                                    }
                                )
                                foundLinks++
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        foundLinks > 0
    } catch (_: Exception) {
        false
    }
}
