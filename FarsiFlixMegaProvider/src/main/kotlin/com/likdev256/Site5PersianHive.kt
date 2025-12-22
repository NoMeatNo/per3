package com.likdev256

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Site 5: PersianHive (https://persianhive.com)
 * Extension functions for FarsiFlixMegaProvider
 */

const val SITE5_URL = "https://persianhive.com"
const val SITE5_NAME = "PersianHive"

fun Element.toSite5SearchResult(): SearchResponse? {
    val title = this.selectFirst("div.pciwgas-title a, div.pciw-title a")?.text()?.trim() ?: return null
    val href = this.selectFirst("a.pciwgas-hover, a.pciw-hover")?.attr("href") ?: return null
    val posterUrl = this.selectFirst("img.pciwgas-img, img.pciw-img")?.attr("src")
    
    val type = if (href.contains("movie") || href.contains("film")) TvType.Movie else TvType.TvSeries

    return if (type == TvType.Movie) {
        newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    } else {
        newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }
}

suspend fun FarsiFlixMegaProvider.loadSite5(url: String, document: Document): LoadResponse? {
    val title = document.selectFirst("h1.pciwgas-title, h1.pciw-title, h1.entry-title, h1")?.text()?.trim() ?: return null
    val poster = fixUrlNull(document.selectFirst("img.pciwgas-img, img.pciw-img")?.attr("src") ?: 
                          document.selectFirst("meta[property=og:image]")?.attr("content"))
    val description = document.selectFirst("div.pciwgas-desc p, div.pciw-desc p, div.entry-content p")?.text()?.trim()
    
    val episodeElements = document.select("article.post-item, div.pciwgas-post-cat-inner")
    val episodes = episodeElements.mapNotNull {
         val epTitle = it.selectFirst("a.pciwgas-hover, h2 a, h3 a")?.text() ?: return@mapNotNull null
         val epUrl = fixUrl(it.selectFirst("a.pciwgas-hover, a")?.attr("href") ?: return@mapNotNull null)
         newEpisode(epUrl) {
             name = epTitle
         }
    }

    return if (episodes.isNotEmpty()) {
        newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.plot = description
        }
    } else {
         newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = description
        }
    }
}

suspend fun FarsiFlixMegaProvider.loadLinksSite5(
    data: String,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    return try {
        val document = app.get(data).document
        
        // Check for iframes
        document.select("iframe").forEach { iframe ->
            val src = iframe.attr("src")
            if (src.isNotBlank()) {
                loadExtractor(src, data, subtitleCallback, callback)
            }
        }

        // Check for PlayerJS source in scripts
        document.select("script").forEach { script ->
            val scriptContent = script.data()
            if (scriptContent.contains("file:")) {
                val fileRegex = Regex("""file\s*:\s*["']([^"']+)["']""")
                fileRegex.findAll(scriptContent).forEach { match ->
                    val videoUrl = match.groupValues[1]
                    if (videoUrl.endsWith(".m3u8") || videoUrl.endsWith(".mp4")) {
                        callback.invoke(
                            newExtractorLink(
                                source = SITE5_NAME,
                                name = "$SITE5_NAME - Stream",
                                url = videoUrl
                            )
                        )
                    }
                }
            }
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
