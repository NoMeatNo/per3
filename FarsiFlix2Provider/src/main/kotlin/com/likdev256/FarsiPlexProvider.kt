package com.likdev256

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.mvvm.safeApiCall
import me.xdrop.fuzzywuzzy.FuzzySearch
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder
import com.lagradost.nicehttp.NiceResponse
import okhttp3.FormBody
import org.jsoup.nodes.Document

class FarsiFlix2Provider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://Farsiplex.com/"
    override var name = "Farsi Flix #2"
    override val hasMainPage = true
    override var lang = "fa"
    override val hasDownloadSupport = true
    override var sequentialMainPage = true
    override var sequentialMainPageDelay: Long = 100
    override val supportedTypes = setOf(
        TvType.Movie, TvType.TvSeries, TvType.Live
    )

    override val mainPage = mainPageOf(
        "$mainUrl/movie/" to "Movies",
        "$mainUrl/tvshow/" to "Series",
        "$mainUrl/live-tv/category/iran.html" to "Live TVs",        
    )

override suspend fun getMainPage(
    page: Int, 
    request: MainPageRequest
): HomePageResponse {
    val link = when (request.name) {
        "Movies" -> "$mainUrl/movie/"
        "Series" -> "$mainUrl/tvshow/"
        "Live TVs" -> "$mainUrl/live-tv/category/iran.html"
        else -> throw IllegalArgumentException("Invalid section name: ${request.name}")
    }

    val document = app.get(link).document
    val home = when (request.name) {
        "Live TVs" -> {
            // For Live TVs, select the 'div.item' elements within 'div.owl-item'
            document.select("figure.col-md-3.col-sm-4.col-xs-6").mapNotNull { it.toLiveTvSearchResult() }
        }
        else -> {
            // For Movies and TV Shows, select 'div.col-md-2.col-sm-3.col-xs-6' elements
            document.select("article.item").mapNotNull { it.toSearchResult() }
        }
    }

    return newHomePageResponse(request.name, home)
}

private fun Element.toLiveTvSearchResult(): LiveSearchResponse? {
    return newLiveSearchResponse(
        name = this.selectFirst("figcaption.figure-caption")?.text() ?: return null,
        url = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null,
    ) {
        type = TvType.Live  // Set type in the lambda block
        posterUrl = fixUrlNull(
            this@toLiveTvSearchResult.select("img").attr("data-src") 
                ?: this@toLiveTvSearchResult.select("img").attr("src")
        )
    }
}

private fun Element.toSearchResult(): SearchResponse? {
    val title = this.selectFirst("div.data h3 a")?.text()?.trim() ?: return null
    val href = fixUrl(this.selectFirst("div.data h3 a")?.attr("href").toString())
    val posterUrl = fixUrlNull(this.selectFirst("div.poster img")?.attr("src")?.trim())
    val type = if (this.hasClass("tvshows")) TvType.TvSeries else TvType.Movie
    return newMovieSearchResponse(title, href, type) {
        this.posterUrl = posterUrl
    }
}

private fun Element.toParseSearchResult(): SearchResponse? {
    val titleElement = this.selectFirst("div.details div.title a")
    val title = titleElement?.text()?.trim() ?: return null
    val href = fixUrl(titleElement.attr("href"))
    val posterUrl = fixUrlNull(this.selectFirst("div.image img")?.attr("src"))
    val type = if (this.hasClass("tvshows")) TvType.TvSeries else TvType.Movie
    return newMovieSearchResponse(title, href, type) {
        this.posterUrl = posterUrl
    }
}
    override suspend fun search(query: String): List<SearchResponse>? {
        val fixedQuery = query.replace(" ", "+")
        val resultFarsi = app.get("$mainUrl/search/$fixedQuery")
            .document.select("div.result-item")
            .mapNotNull { it.toParseSearchResult() }

        return resultFarsi.sortedBy { -FuzzySearch.partialRatio(it.name.replace("(\\()+(.*)+(\\))".toRegex(), "").lowercase(), query.lowercase()) }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val isTvSeries = url.contains("/tvshow/")
        val isMovie = url.contains("/movie/")
        val isEpisode = url.contains("/episode/")

        return if (isTvSeries) {
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
                })  // Properly close the lambda
            }
        }

        newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.plot = plot
        }
    } else if (isMovie) {
        // Adjust the selectors for movies
        val title = document.selectFirst("div.data h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.playbox img.cover")?.attr("src"))
        val plot = document.selectFirst("div#info div.wp-content p")?.text()?.trim()

        newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = plot
        }
    } else if (isEpisode) {
        // Adjust the selectors for movies
        val title = document.selectFirst("div#info h2")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("#fakeplayer .playbox img.cover")?.attr("src"))
   // w2     val poster = fixUrlNull(document.selectFirst("#dt_galery .g-item img")?.attr("src"))
        val plot = document.selectFirst("div#info div.wp-content p")?.text()?.trim()

        newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = plot
        }
    } else {
        // If neither "/tvshows/" nor "/movies/" is found, assume it's a movie by default
        val title = document.selectFirst("div.data h2")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
        val plot = document.selectFirst("div#info div.wp-content p")?.text()?.trim()

        newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = plot
        }
    }
}

override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    return try {
        val document = app.get(data).document
        var foundLinks = 0
        
        // Method 1: Check if page has a watch form to navigate to player page
        // This is required for episode pages which have form id="watch-XXXXX"
        val watchForm = document.selectFirst("form[id^=watch-]")
        val playerDocument = if (watchForm != null) {
            val formAction = watchForm.attr("action")
            val formId = watchForm.selectFirst("input[name=id]")?.attr("value") ?: ""
            
            if (formAction.isNotEmpty() && formId.isNotEmpty()) {
                // Submit the form to get the player page
                app.post(
                    formAction,
                    data = mapOf("id" to formId),
                    referer = data
                ).document
            } else {
                document
            }
        } else {
            document
        }
        
        // Method 2: Extract video URLs from iframes on the player page
        // The iframes have the video source URL encoded in the "source=" parameter
        playerDocument.select("iframe.metaframe, iframe.rptss, iframe[src*='source=']").forEach { iframe ->
            val iframeSrc = iframe.attr("src")
            if (iframeSrc.contains("source=")) {
                val sourceParam = iframeSrc.substringAfter("source=").substringBefore("&")
                val decodedUrl = java.net.URLDecoder.decode(sourceParam, "UTF-8")
                
                if (decodedUrl.isNotBlank() && (decodedUrl.endsWith(".mp4") || decodedUrl.endsWith(".m3u8"))) {
                    val quality = when {
                        decodedUrl.contains("1080") -> Qualities.P1080.value
                        decodedUrl.contains("720") -> Qualities.P720.value
                        decodedUrl.contains("480") -> Qualities.P480.value
                        decodedUrl.contains("360") -> Qualities.P360.value
                        else -> Qualities.Unknown.value
                    }
                    
                    val qualityLabel = when (quality) {
                        Qualities.P1080.value -> "1080p"
                        Qualities.P720.value -> "720p"
                        Qualities.P480.value -> "480p"
                        Qualities.P360.value -> "360p"
                        else -> "Unknown"
                    }
                    
                    callback.invoke(
                        newExtractorLink(
                            source = this.name,
                            name = "${this.name} - $qualityLabel",
                            url = decodedUrl
                        ).apply {
                            this.quality = quality
                            this.referer = mainUrl
                        }
                    )
                    foundLinks++
                }
            }
        }
        
        // Method 3: If no iframes found yet, try the dooplayer API
        if (foundLinks == 0) {
            val postId = playerDocument.selectFirst("li.dooplay_player_option[data-post]")?.attr("data-post")
                ?: playerDocument.selectFirst("[data-post]")?.attr("data-post")
            
            if (postId != null) {
                val playerOptions = playerDocument.select("li.dooplay_player_option")
                playerOptions.forEach { option ->
                    val nume = option.attr("data-nume")
                    val dataType = option.attr("data-type")
                    val qualityLabel = option.selectFirst("span.title")?.text() ?: "Unknown"
                    
                    try {
                        // Call the dooplayer API
                        val apiUrl = "$mainUrl/wp-json/dooplayer/v2/$postId/$dataType/$nume"
                        val apiResponse = app.get(apiUrl).text
                        
                        // Parse the response to get the embed URL
                        val embedUrlRegex = Regex(""""embed_url"\s*:\s*"([^"]+)"""")
                        val embedMatch = embedUrlRegex.find(apiResponse)
                        if (embedMatch != null) {
                            val embedUrl = embedMatch.groupValues[1].replace("\\/", "/")
                            
                            // Extract the source parameter from the iframe URL
                            if (embedUrl.contains("source=")) {
                                val sourceParam = embedUrl.substringAfter("source=").substringBefore("&")
                                val decodedUrl = java.net.URLDecoder.decode(sourceParam, "UTF-8")
                                
                                if (decodedUrl.isNotBlank() && (decodedUrl.endsWith(".mp4") || decodedUrl.endsWith(".m3u8"))) {
                                    val quality = when {
                                        qualityLabel.contains("1080") || decodedUrl.contains("1080") -> Qualities.P1080.value
                                        qualityLabel.contains("720") || decodedUrl.contains("720") -> Qualities.P720.value
                                        qualityLabel.contains("480") || decodedUrl.contains("480") -> Qualities.P480.value
                                        qualityLabel.contains("360") || decodedUrl.contains("360") -> Qualities.P360.value
                                        else -> Qualities.Unknown.value
                                    }
                                    
                                    callback.invoke(
                                        newExtractorLink(
                                            source = this.name,
                                            name = "${this.name} - ${qualityLabel}p",
                                            url = decodedUrl
                                        ).apply {
                                            this.quality = quality
                                            this.referer = mainUrl
                                        }
                                    )
                                    foundLinks++
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Silent fail for individual player options
                    }
                }
            }
        }
        
        // Method 4: Check for direct video sources in scripts (fallback)
        if (foundLinks == 0) {
            val scriptContent = playerDocument.selectFirst("script:containsData('video/mp4')")?.data() ?: ""
            val mp4LinkRegex = Regex("""src:\s*['"]?(https?://[^'"]+\.mp4)['"]?""")
            mp4LinkRegex.findAll(scriptContent).forEach { matchResult ->
                val mp4Link = matchResult.groupValues[1]
                if (mp4Link.isNotBlank()) {
                    val quality = when {
                        mp4Link.contains("1080") -> Qualities.P1080.value
                        mp4Link.contains("720") -> Qualities.P720.value
                        mp4Link.contains("480") -> Qualities.P480.value
                        else -> Qualities.Unknown.value
                    }
                    callback.invoke(
                        newExtractorLink(
                            source = this.name,
                            name = this.name,
                            url = mp4Link
                        ).apply {
                            this.quality = quality
                            this.referer = mainUrl
                        }
                    )
                    foundLinks++
                }
            }
        }
        
        foundLinks > 0
    } catch (e: Exception) {
        false
    }
}
        
    private suspend fun getUrls(url: String): List<String>? {

        return app.get(url).document.selectFirst("#ib-4-f > script:nth-child(4)")?.data()
            ?.substringAfter("const urls = [")?.substringBefore("]")?.trim()?.replace(",'',", "")
            ?.split(",")?.toList()
    }

    data class Response(
        @JsonProperty("hits") var hits: Hits? = Hits()
    )

    data class Hits(
        @JsonProperty("hits") var hitslist: ArrayList<HitsList> = arrayListOf()
    )

    data class HitsList(
        @JsonProperty("_source") var source: Source? = Source()
    )

    data class Source(
        @JsonProperty("location") var location: String? = null,
        @JsonProperty("title") var title: String? = null,
        @JsonProperty("image_link") var imageLink: String? = null,
    )
}
