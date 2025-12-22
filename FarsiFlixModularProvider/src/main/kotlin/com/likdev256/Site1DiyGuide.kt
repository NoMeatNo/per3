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
 * Handles movies and TV series from DiyGuide.
 */
class Site1DiyGuide(override val api: MainAPI) : SiteHandler {
    override val siteUrl = "https://www.diycraftsguide.com"
    override val siteName = "DiyGuide"
    
    override val mainPages = listOf(
        "$siteUrl/movies.html" to "Movies - $siteName",
        "$siteUrl/tv-series.html" to "Series - $siteName",
    )
    
    override fun getHomeSelector(url: String): String = "div.col-md-2.col-sm-3.col-xs-6"
    
    override fun parseHomeItem(element: Element): SearchResponse? {
        val title = element.selectFirst("div.movie-title h3 a")?.text()?.trim() ?: return null
        val href = element.selectFirst("div.movie-title h3 a")?.attr("href")?.let { fixUrl(it) } ?: return null
        val posterUrl = element.selectFirst("div.latest-movie-img-container")?.attr("data-src")?.trim()?.let { fixUrlNull(it) }
        return with(api) {
            newMovieSearchResponse(title, href, TvType.Movie) {
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
