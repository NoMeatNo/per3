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

class FarsiProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://farsiland.com"
    override var name = "Persian World Farsi"
    override val hasMainPage = true
    override var lang = "fa"
    override val hasDownloadSupport = true
    override var sequentialMainPage = true
    override var sequentialMainPageDelay: Long = 100
    override val supportedTypes = setOf(
        TvType.Movie, TvType.TvSeries, TvType.Live
    )

    override val mainPage = mainPageOf(
        "$mainUrl/fill1.html" to "Movies",
        "$mainUrl/series-21/" to "TV Shows",
        "$mainUrl/live-tv/category/iran.html" to "Live TVs",        
    )

override suspend fun getMainPage(
    page: Int, 
    request: MainPageRequest
): HomePageResponse {
    val link = when (request.name) {
        "Movies" -> "$mainUrl/fill1.html"
        "TV Shows" -> "$mainUrl/series-21/"
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
    return LiveSearchResponse(
        this.selectFirst("figcaption.figure-caption")?.text() ?: return null,
        fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null,
        this@FarsiProvider.name, // This provides the type
        TvType.Live,
        fixUrlNull(this.select("img").attr("data-src")),
    )
}


    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div.data h3 a")?.text()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("div.data h3 a")?.attr("href").toString())
        val posterUrl = fixUrlNull(this.selectFirst("div.poster img")?.attr("data-src"))
        val type = if (this.hasClass("tvshows")) TvType.TvSeries else TvType.Movie
        return newMovieSearchResponse(title, href, type) {
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
    val title = document.selectFirst("div.data h1")?.text()?.trim() ?: return null
    val poster = fixUrlNull(document.selectFirst("div.poster img")?.attr("src"))
    val plot = document.selectFirst("div.contenido p")?.text()?.trim()

    val isTvSeries = url.contains("/tvshows/")
    
    return if (isTvSeries) {
        val episodes = mutableListOf<Episode>()
        document.select("#seasons .se-c").forEach { season ->
            val seasonNumber = season.selectFirst(".se-t")?.text()?.toIntOrNull() ?: return@forEach
            season.select("ul.episodios li").forEach { episode ->
                val epNumber = episode.selectFirst(".numerando")?.text()?.substringAfter("-")?.trim()?.toIntOrNull() ?: return@forEach
                val epTitle = episode.selectFirst(".episodiotitle a")?.text() ?: return@forEach
                val epLink = fixUrl(episode.selectFirst(".episodiotitle a")?.attr("href") ?: return@forEach)
                episodes.add(Episode(epLink, epTitle, seasonNumber, epNumber))
            }
        }
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
