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

class IBommaProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://www.radiovatani.com"
    override var name = "Persian World 2"
    override val hasMainPage = true
    override var lang = "fa"
    override val hasDownloadSupport = true
    override var sequentialMainPage = true
    override var sequentialMainPageDelay: Long = 100
    override val supportedTypes = setOf(
        TvType.Movie, TvType.TvSeries
    )

    override val mainPage = mainPageOf(
        "$mainUrl/fill1.html" to "Movies",
        "$mainUrl/sell1.html" to "TV Shows",
        "$mainUrl/live-tv.html" to "Live TVs",        
    )

    override suspend fun getMainPage(
        page: Int, 
        request: MainPageRequest
    ): HomePageResponse {
        val link = when (request.name) {
            "Movies" -> "$mainUrl/fill1.html"
            "TV Shows" -> "$mainUrl/sell1.html"
            "Live TVs" -> "$mainUrl/live-tv.html"
            else -> throw IllegalArgumentException("Invalid section name: ${request.name}")
        }
        val document = app.get(link).document
        val home = document.select("div.col-md-2.col-sm-3.col-xs-6").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
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
        val title = document.selectFirst("div.col-sm-9 p.m-t-10 strong")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("video#play")?.attr("poster"))
        val tvType = if (document.select(".col-md-12.col-sm-12:has(div.owl-carousel)").isNotEmpty()) TvType.TvSeries else TvType.Movie

        return if (tvType == TvType.TvSeries) {
            val episodes = document.select(".owl-carousel .item").mapNotNull {
                val figcaption = it.select(".figure figcaption").text().trim()
                val episode = figcaption.filter { it.isDigit() }.toIntOrNull()
                val seasonNumberElement = item.parents().select(".movie-heading span").firstOrNull()
                val season = seasonNumberElement?.text()?.removePrefix("Season")?.trim()?.toIntOrNull()
                val seasonInfo = seasonNumberElement?.text()?.trim() ?: ""
      
                val name = "$figcaption - $seasonInfo"
                
                val href = fixUrl(it.select("a").attr("href")?: return@mapNotNull null)
                Episode(
                    href,
                    name,
                    season,
                    episode
                )
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
