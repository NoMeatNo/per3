package com.likdev256

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Site 6: PersianMovieBoxx (persianmovieboxx.com)
 * Handles Iranian movies, TV series (serials), and Live TV videos.
 */
class Site6PersianMovieBoxx(override val api: MainAPI) : SiteHandler {
    override val siteUrl = "https://persianmovieboxx.com"
    override val siteName = "PersianMovieBoxx"
    
    override val mainPages = listOf(
        "$siteUrl/iranian-movies/" to "Movies - $siteName",
        "$siteUrl/serial/" to "Serial - $siteName",
        "$siteUrl/videos/" to "Live TV - $siteName",
    )
    
    override fun getHomeSelector(url: String): String {
        return when {
            url.contains("iranian-movies") -> "div.movies__inner > div.movie"
            url.contains("serial") -> "div.tv-show"
            url.contains("videos") -> "div.videos__inner > div.video"
            else -> "div.movie"
        }
    }
    
    override fun parseHomeItem(element: Element): SearchResponse? {
        // Determine type based on element class
        val isMovie = element.hasClass("movie") || element.className().contains("type-movie")
        val isTvShow = element.hasClass("tv-show") || element.className().contains("type-tv_show")
        val isVideo = element.hasClass("video") || element.className().contains("type-video")
        
        // Extract title and href
        val linkElement = element.selectFirst("a.movie__link, a.tv-show__link, a.video__link, .movie__title a, .tv-show__title a, .video__title a, h3 a, h2 a, a[href]")
        val href = linkElement?.attr("href")?.let { fixUrl(it) } ?: return null
        
        // Title from h3/h2 or link text
        val title = element.selectFirst("h3.movie__title, h3.tv-show__title, h3.video__title, h3 a, h2 a")?.text()?.trim()
            ?: linkElement?.text()?.trim()
            ?: return null
        
        // Poster image
        val posterUrl = element.selectFirst("img.movie__poster--image, img.tv-show__poster--image, img.video__poster--image, img")?.let {
            it.attr("src").ifBlank { it.attr("data-src") }.ifBlank { it.attr("data-lazy-src") }
        }?.let { fixUrlNull(it) }
        
        return with(api) {
            when {
                isTvShow -> newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                    this.posterUrl = posterUrl
                }
                isVideo -> newMovieSearchResponse(title, href, TvType.Live) {
                    this.posterUrl = posterUrl
                }
                else -> newMovieSearchResponse(title, href, TvType.Movie) {
                    this.posterUrl = posterUrl
                }
            }
        }
    }
    
    override suspend fun search(query: String): List<SearchResponse> {
        return try {
            val searchUrl = "$siteUrl/?s=${query.replace(" ", "+")}&post_type=movie"
            app.get(searchUrl).document
                .select("div.movies__inner > div.movie, div.tv-show, div.video")
                .mapNotNull { parseHomeItem(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }
    
    override suspend fun load(url: String, document: Document): LoadResponse? {
        val title = document.selectFirst("h1.entry-title, h1.movie__title, h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
            ?: fixUrlNull(document.selectFirst("img.movie-poster, .movie__poster img, img.wp-post-image")?.attr("src"))
        val description = document.selectFirst("div.movie__short-description, div.entry-content p, .description")?.text()?.trim()
        val year = document.selectFirst(".movie__meta--release-year, .release-year")?.text()?.toIntOrNull()
        
        // Check if this is a TV show with seasons/episodes
        val seasons = document.select("div.season, .episodes-list, .season-list")
        if (seasons.isNotEmpty() || url.contains("tv_show") || url.contains("serial")) {
            val episodes = mutableListOf<Episode>()
            
            // Try to find episodes
            document.select("div.episode, .episode-item, a.episode-link").forEach { ep ->
                val epTitle = ep.selectFirst("h4, .episode-title, a")?.text()?.trim() ?: "Episode"
                val epUrl = ep.selectFirst("a")?.attr("href")?.let { fixUrl(it) } ?: url
                val epNumber = Regex("""(\d+)""").find(epTitle)?.groupValues?.get(1)?.toIntOrNull()
                
                episodes.add(with(api) {
                    newEpisode(epUrl) {
                        name = epTitle
                        episode = epNumber
                    }
                })
            }
            
            // If no episodes found, treat the page itself as a single episode
            if (episodes.isEmpty()) {
                episodes.add(with(api) {
                    newEpisode(url) {
                        name = title
                        episode = 1
                    }
                })
            }
            
            return with(api) {
                newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster
                    this.plot = description
                    this.year = year
                }
            }
        }
        
        // Regular movie/video
        return with(api) {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
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
            val document = app.get(data).document
            
            // Check for iframes (common video embeds)
            document.select("iframe").forEach { iframe ->
                val src = iframe.attr("src").ifBlank { iframe.attr("data-src") }
                if (src.isNotBlank()) {
                    try {
                        loadExtractor(src, data, subtitleCallback, callback)
                        foundLinks = true
                    } catch (_: Exception) {}
                }
            }
            
            // Check for video elements
            document.select("video source").forEach { source ->
                val src = source.attr("src")
                if (src.isNotBlank()) {
                    callback.invoke(
                        newExtractorLink(
                            source = siteName,
                            name = "$siteName - Direct",
                            url = fixUrl(src)
                        ) {
                            this.quality = Qualities.Unknown.value
                            this.referer = data
                        }
                    )
                    foundLinks = true
                }
            }
            
            // Check for player.js or file: patterns in scripts
            document.select("script").forEach { script ->
                val scriptContent = script.data()
                if (scriptContent.contains("file:") || scriptContent.contains("sources:")) {
                    val fileRegex = Regex("""file\s*:\s*["']([^"']+)["']""")
                    fileRegex.findAll(scriptContent).forEach { match ->
                        val videoUrl = match.groupValues[1]
                        if (videoUrl.endsWith(".m3u8") || videoUrl.endsWith(".mp4") || videoUrl.contains("playlist")) {
                            callback.invoke(
                                newExtractorLink(
                                    source = siteName,
                                    name = "$siteName - Stream",
                                    url = videoUrl
                                ) {
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
