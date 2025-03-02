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

class FarsiPlexProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://farsiplex.com/"
    override var name = "Farsi Plex"
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
        // Step 1: Load initial page containing the play link
        val initialDocument = app.get(data).document

        // Step 2: Extract the initial play link from the anchor
        val playLink = initialDocument.selectFirst("a[href*='/play/?id=']")?.attr("href") ?: return false
        val playPage = app.get(playLink).document

        // Step 3: Find Player 3 link with videojs parameter
        val player3Link = playPage.selectFirst("a[href*='pname=videojs']")?.attr("href") ?: return false
        val player3Page = app.get(player3Link).document

        // Step 4: Extract all available video sources
        val videoSources = player3Page.select("video source")
        val availableSources = videoSources.mapNotNull { element ->
            val label = element.attr("label")
            val src = element.attr("src")
            if (src.isNotBlank()) Pair(label, src) else null
        }

        if (availableSources.isEmpty()) return false

        // Quality selection logic with fallback
        val selectedSource = availableSources.firstOrNull { it.first == "720" }
            ?: availableSources.firstOrNull { it.first == "480" }
            ?: availableSources.firstOrNull { it.first == "1080" }
            ?: availableSources.first()

        // Determine quality value
        val qualityValue = when (selectedSource.first) {
            "720" -> Qualities.P720.value
            "480" -> Qualities.P480.value
            "1080" -> Qualities.P1080.value
            else -> Qualities.P480.value // Default to 480 if unknown
        }

        callback.invoke(
            ExtractorLink(
                this.name,
                this.name,
                selectedSource.second,
                referer = player3Link,
                quality = qualityValue
            )
        )
        true
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
