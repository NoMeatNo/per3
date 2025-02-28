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

class FarsiFlixProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://www.diycraftsguide.com"
    override var name = "Farsi Flix #1"
    override val hasMainPage = true
    override var lang = "fa"
    override val hasDownloadSupport = true
    override var sequentialMainPage = true
    override var sequentialMainPageDelay: Long = 100
    override val supportedTypes = setOf(
        TvType.Movie, TvType.TvSeries, TvType.Live
    )

    override val mainPage = mainPageOf(
        "$mainUrl/movies.html" to "Movies",
        "$mainUrl/tv-series.html" to "TV Shows",
        "$mainUrl/live-tv/category/iran.html" to "Live TVs",        
    )

override suspend fun getMainPage(
    page: Int, 
    request: MainPageRequest
): HomePageResponse {
    val link = when (request.name) {
        "Movies" -> "$mainUrl/movies.html"
        "TV Shows" -> "$mainUrl/tv-series.html"
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
            document.select("div.col-md-2.col-sm-3.col-xs-6").mapNotNull { it.toSearchResult() }
        }
    }

    return newHomePageResponse(request.name, home)
}

private fun Element.toLiveTvSearchResult(): LiveSearchResponse? {
    return LiveSearchResponse(
        this.selectFirst("figcaption.figure-caption")?.text() ?: return null,
        fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null,
        this@FarsiFlixProvider.name, // This provides the type
        TvType.Live,
        fixUrlNull(this.select("img").attr("data-src")),
    )
}


    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div.movie-title h3 a")?.text()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("div.movie-title h3 a")?.attr("href").toString())
        val posterUrl = fixUrlNull(this.selectFirst("div.latest-movie-img-container")?.attr("data-src")?.trim())
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val fixedQuery = query.replace(" ", "+")
        val resultFarsi = app.get("$mainUrl/search?q=$fixedQuery")
            .document.select("div.col-md-2.col-sm-3.col-xs-6")
            .mapNotNull { it.toSearchResult() }

        return resultFarsi.sortedBy { -FuzzySearch.partialRatio(it.name.replace("(\\()+(.*)+(\\))".toRegex(), "").lowercase(), query.lowercase()) }
    }

override suspend fun load(url: String): LoadResponse? {
    val document = app.get(url).document
     val data =
        document.select("script").find { it.data().contains("var channelName =") }?.data()
    val title = document.selectFirst("div.col-sm-9 p.m-t-10 strong")?.text()?.trim() ?: return null
    val poster = fixUrlNull(document.selectFirst("video#play")?.attr("poster"))
    val baseUrl = data?.substringAfter("baseUrl = \"")?.substringBefore("\";")
    val channel = data?.substringAfter("var channelName = \"")?.substringBefore("\";")
    val isTvSeries = document.select(".col-md-12.col-sm-12:has(div.owl-carousel)").isNotEmpty()
    val isLiveTv = document.select(".col-md-12.col-sm-12:has(div.live_tv_owl)").isNotEmpty()
        return when {
        isTvSeries -> {
            val title = document.selectFirst("div.col-sm-9 p.m-t-10 strong")?.text()?.trim() ?: return null
            val poster = fixUrlNull(document.selectFirst("video#play")?.attr("poster"))
            val episodes = mutableListOf<Episode>()
            val rows = document.select(".row")
            var seasonNumber = 0
            rows.forEachIndexed { index, row ->
                if (row.select(".movie-heading").isNotEmpty()) {
                    // This row contains season information
                    val seasonText = row.select(".movie-heading span").text().trim()
                    seasonNumber = seasonText.removePrefix("Season").trim().toIntOrNull() ?: 0
                } else if (row.select(".owl-carousel").isNotEmpty() && seasonNumber > 0) {
                    // This row contains episodes for the previously identified season
                    val items = row.select(".item")
                    items.forEach { item ->
                        val episodeLink = item.select("a").attr("href")
                        val episodeNumberText = item.select(".figure-caption").text().trim()
                        val episodeNumber = episodeNumberText.removePrefix("E").toIntOrNull() ?: 0
                        val episodeName = "Season $seasonNumber Episode $episodeNumber"
                        if (episodeLink.isNotEmpty()) {
                            episodes.add(Episode(episodeLink, episodeName, seasonNumber, episodeNumber))
                        }
                    }
                }
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
            }
        }
isLiveTv -> {
    val title = document.selectFirst(".media-heading")?.text()?.trim() ?: return null
    val posterUrl = fixUrlNull(document.selectFirst("img.media-object")?.attr("src")) ?: ""
    val plot = document.select("p.live").text()
    val liveTvUrl = document.selectFirst(".btn-group a")?.attr("href") ?: return null
    val scriptContent = document.selectFirst("script:containsData('videojs')")?.data() ?: ""
    val m3u8LinkRegex = Regex("""src: '(https?://[^']+\.m3u8)'""")
    val matchResult = m3u8LinkRegex.find(scriptContent)
    val dataUrl = matchResult?.groupValues?.get(1)

    return LiveStreamLoadResponse(
        name = title,
        url = liveTvUrl,
        apiName = this.name, // Assuming this.name is the apiName for LiveStreamLoadResponse
        dataUrl = dataUrl ?: "",
        posterUrl = posterUrl,
        plot = plot
    )
}


        else -> {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
            }
        }
    }
}

    
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        data.split(",").forEach { url ->
            val document = app.get(url.trim()).document
            val scriptContent = document.selectFirst("script:containsData('video/mp4')")?.data() ?: ""
            val mp4LinkRegex = Regex("""src: '(https?://[^']+\.mp4)'""")
            val matchResults = mp4LinkRegex.findAll(scriptContent)
        matchResults.forEach { matchResult ->
                val mp4Link = matchResult.groupValues[1]
                callback.invoke(
                    ExtractorLink(
                        this.name,
                        this.name,
                        mp4Link,
                        referer = url.trim(),
                        quality = Qualities.Unknown.value,
                    )
                )
            }
        }
        return true
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
