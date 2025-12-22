package com.likdev256

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Site 2: FarsiPlex (farsiplex.com)
 * Handles movies and TV series from FarsiPlex.
 */
class Site2FarsiPlex : SiteHandler {
    override val siteUrl = "https://farsiplex.com"
    override val siteName = "FarsiPlex"
    
    override val mainPages = listOf(
        "$siteUrl/movie/" to "Movies - $siteName",
        "$siteUrl/tvshow/" to "Series - $siteName",
    )
    
    override fun getHomeSelector(url: String): String = "article.item"
    
    override fun parseHomeItem(element: Element): SearchResponse? {
        val title = element.selectFirst("div.data h3 a")?.text()?.trim() ?: return null
        val href = element.selectFirst("div.data h3 a")?.attr("href")?.let { fixUrl(it) } ?: return null
        val posterUrl = element.selectFirst("div.poster img")?.attr("src")?.trim()?.let { fixUrlNull(it) }
        val type = if (element.hasClass("tvshows")) TvType.TvSeries else TvType.Movie
        return newMovieSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
        }
    }
    
    fun parseSearchItem(element: Element): SearchResponse? {
        val titleElement = element.selectFirst("div.details div.title a")
        val title = titleElement?.text()?.trim() ?: return null
        val href = fixUrl(titleElement.attr("href"))
        val posterUrl = fixUrlNull(element.selectFirst("div.image img")?.attr("src"))
        val type = if (element.hasClass("tvshows")) TvType.TvSeries else TvType.Movie
        return newMovieSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
        }
    }
    
    override suspend fun search(query: String): List<SearchResponse> {
        return try {
            app.get("$siteUrl/?s=${query.replace(" ", "+")}")
                .document.select("div.result-item")
                .mapNotNull { parseSearchItem(it) }
        } catch (e: Exception) { emptyList() }
    }
    
    override suspend fun load(url: String, document: Document): LoadResponse? {
        val isTvSeries = url.contains("/tvshow/")
        val isMovie = url.contains("/movie/")
        val isEpisode = url.contains("/episode/")

        return when {
            isTvSeries -> {
                val title = document.selectFirst("div.data h1")?.text()?.trim() ?: return null
                val poster = fixUrlNull(document.selectFirst("div.poster img")?.attr("src"))
                val plot = document.selectFirst("div.contenido p")?.text()?.trim()
                val episodes = mutableListOf<Episode>()

                document.select("#seasons .se-c").forEach { seasonElement ->
                    val seasonNumber = seasonElement.selectFirst(".se-t")?.text()?.toIntOrNull() ?: return@forEach
                    seasonElement.select("ul.episodios li").forEach { episodeElement ->
                        val epNumber = episodeElement.selectFirst(".numerando")?.text()
                            ?.substringAfter("-")?.trim()?.toIntOrNull() ?: return@forEach
                        val epTitle = episodeElement.selectFirst(".episodiotitle a")?.text() ?: return@forEach
                        val epLink = fixUrl(episodeElement.selectFirst(".episodiotitle a")?.attr("href") ?: return@forEach)

                        episodes.add(newEpisode(epLink) {
                            name = epTitle
                            season = seasonNumber
                            episode = epNumber
                        })
                    }
                }

                newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster
                    this.plot = plot
                }
            }
            isMovie -> {
                val title = document.selectFirst("div.data h1")?.text()?.trim() ?: return null
                val poster = fixUrlNull(document.selectFirst("div.playbox img.cover")?.attr("src"))
                val plot = document.selectFirst("div#info div.wp-content p")?.text()?.trim()

                newMovieLoadResponse(title, url, TvType.Movie, url) {
                    this.posterUrl = poster
                    this.plot = plot
                }
            }
            isEpisode -> {
                val title = document.selectFirst("div#info h2")?.text()?.trim() ?: return null
                val poster = fixUrlNull(document.selectFirst("#fakeplayer .playbox img.cover")?.attr("src"))
                val plot = document.selectFirst("div#info div.wp-content p")?.text()?.trim()

                newMovieLoadResponse(title, url, TvType.Movie, url) {
                    this.posterUrl = poster
                    this.plot = plot
                }
            }
            else -> {
                val title = document.selectFirst("div.data h2")?.text()?.trim() ?: return null
                val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
                val plot = document.selectFirst("div#info div.wp-content p")?.text()?.trim()

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
            
            // Get post ID from the watch form
            val watchForm = document.selectFirst("form[id^=watch-]")
            val postId = watchForm?.selectFirst("input[name=id]")?.attr("value") ?: ""
            
            if (postId.isNotEmpty()) {
                // Try dooplayer API to get embed URLs for each quality
                for (nume in 1..3) {
                    try {
                        val apiUrl = "$siteUrl/wp-json/dooplayer/v2/$postId/tv/$nume"
                        val apiResponse = app.get(apiUrl).text
                        
                        val embedUrlRegex = Regex(""""embed_url"\s*:\s*"([^"]+)"""")
                        val embedMatch = embedUrlRegex.find(apiResponse)
                        
                        if (embedMatch != null) {
                            val embedUrl = embedMatch.groupValues[1].replace("\\/", "/")
                            
                            // Fetch the jwplayer page to get the REAL video URL with md5 token
                            if (embedUrl.contains("jwplayer")) {
                                try {
                                    val jwPlayerHtml = app.get(embedUrl).text
                                    
                                    // Extract the jw.file value which contains the real playable URL
                                    val jwFileRegex = Regex(""""file"\s*:\s*"([^"]+)"""")
                                    val jwFileMatch = jwFileRegex.find(jwPlayerHtml)
                                    
                                    if (jwFileMatch != null) {
                                        val realVideoUrl = jwFileMatch.groupValues[1].replace("\\/", "/")
                                        
                                        if (realVideoUrl.isNotBlank() && (realVideoUrl.contains(".mp4") || realVideoUrl.contains(".m3u8"))) {
                                            val quality = when {
                                                realVideoUrl.contains("1080") -> Qualities.P1080.value
                                                realVideoUrl.contains("720") -> Qualities.P720.value
                                                realVideoUrl.contains("480") -> Qualities.P480.value
                                                realVideoUrl.contains("360") -> Qualities.P360.value
                                                else -> Qualities.Unknown.value
                                            }
                                            
                                            val qualityLabel = when (quality) {
                                                Qualities.P1080.value -> "1080p"
                                                Qualities.P720.value -> "720p"
                                                Qualities.P480.value -> "480p"
                                                Qualities.P360.value -> "360p"
                                                else -> ""
                                            }
                                            
                                            callback.invoke(
                                                newExtractorLink(
                                                    source = siteName,
                                                    name = "$siteName $qualityLabel",
                                                    url = realVideoUrl
                                                ).apply {
                                                    this.quality = quality
                                                    this.referer = siteUrl
                                                }
                                            )
                                            foundLinks++
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
            
            // Fallback: try player options from page
            if (foundLinks == 0) {
                val playerDocument = if (watchForm != null) {
                    val formAction = watchForm.attr("action")
                    val formId = watchForm.selectFirst("input[name=id]")?.attr("value") ?: ""
                    if (formAction.isNotEmpty() && formId.isNotEmpty()) {
                        app.post(formAction, data = mapOf("id" to formId), referer = data).document
                    } else document
                } else document
                
                val dataPostId = playerDocument.selectFirst("li.dooplay_player_option[data-post]")?.attr("data-post")
                    ?: playerDocument.selectFirst("[data-post]")?.attr("data-post")
                
                if (dataPostId != null) {
                    val playerOptions = playerDocument.select("li.dooplay_player_option")
                    playerOptions.forEach { option ->
                        val nume = option.attr("data-nume")
                        val dataType = option.attr("data-type")
                        
                        try {
                            val apiUrl = "$siteUrl/wp-json/dooplayer/v2/$dataPostId/$dataType/$nume"
                            val apiResponse = app.get(apiUrl).text
                            
                            val embedUrlRegex = Regex(""""embed_url"\s*:\s*"([^"]+)"""")
                            val embedMatch = embedUrlRegex.find(apiResponse)
                            
                            if (embedMatch != null) {
                                val embedUrl = embedMatch.groupValues[1].replace("\\/", "/")
                                
                                if (embedUrl.contains("jwplayer")) {
                                    val jwPlayerHtml = app.get(embedUrl).text
                                    val jwFileRegex = Regex(""""file"\s*:\s*"([^"]+)"""")
                                    val jwFileMatch = jwFileRegex.find(jwPlayerHtml)
                                    
                                    if (jwFileMatch != null) {
                                        val realVideoUrl = jwFileMatch.groupValues[1].replace("\\/", "/")
                                        
                                        if (realVideoUrl.isNotBlank() && realVideoUrl.contains(".mp4")) {
                                            val quality = when {
                                                realVideoUrl.contains("1080") -> Qualities.P1080.value
                                                realVideoUrl.contains("720") -> Qualities.P720.value
                                                realVideoUrl.contains("480") -> Qualities.P480.value
                                                else -> Qualities.Unknown.value
                                            }
                                            
                                            callback.invoke(
                                                newExtractorLink(
                                                    source = siteName,
                                                    name = siteName,
                                                    url = realVideoUrl
                                                ).apply {
                                                    this.quality = quality
                                                    this.referer = siteUrl
                                                }
                                            )
                                            foundLinks++
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
            
            foundLinks > 0
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
