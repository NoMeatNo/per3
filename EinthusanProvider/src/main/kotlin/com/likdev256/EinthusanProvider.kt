package com.likdev256

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.mvvm.safeApiCall
import me.xdrop.fuzzywuzzy.FuzzySearch
import org.jsoup.nodes.Element
import com.lagradost.nicehttp.NiceResponse
import okhttp3.FormBody

class EinthusanProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://www.radiovatani.com"
    override var name = "Persian World"
    override val hasMainPage = true
    override var lang = "fa"
    override val hasDownloadSupport = true
    override var sequentialMainPage = true
    override var sequentialMainPageDelay: Long = 100
    override val supportedTypes = setOf(
        TvType.Movie
    )

    override val mainPage = mainPageOf(
        "/fill1.html" to "Movies",
        "/sell1.html" to "TV Shows",
        "/live-tv.html" to "Live TVs",        
    )

    override suspend fun getMainPage(
        page: Int, 
        request: MainPageRequest
    ): HomePageResponse {
        val link = "$mainUrl/saff1"
        val document = app.get(link).document
        val home = document.select("div.col-md-2.col-sm-3.col-xs-6").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div.movie-title h3 a")?.text()?.trim() ?: return null
        val href = this.selectFirst("div.movie-title h3 a")?.attr("href").toString()
        val posterUrl = this.selectFirst("div.latest-movie-img-container")?.attr("data-src")?.trim()

        return newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.quality = SearchQuality.HD
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val fixedQuery = query.replace(" ", "+")
        val resultTamil = app.get("$mainUrl/search?q=$fixedQuery")
            .document.select("div.col-md-2.col-sm-3.col-xs-6")
            .mapNotNull { it.toSearchResult() }

        return resultTamil.sortedBy { -FuzzySearch.partialRatio(it.name.replace("(\\()+(.*)+(\\))".toRegex(), "").lowercase(), query.lowercase()) }
    }


    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val title = doc.select("div.col-sm-9 p.m-t-10 strong").text().trim() ?: return null
        val href = fixUrl(mainUrl + doc.select("#UIMovieSummary > ul > li > div.block2 > a.title").attr("href").toString())
        val poster = doc.selectFirst("video#play")?.attr("poster")
        val tags = doc.select("a.btn-tags").map { it.text() }
        val year =
            doc.selectFirst("div.block2 > div.info > p")?.ownText()?.trim()?.toInt()
        val description = doc.selectFirst("p.synopsis")?.text()?.trim()
        val rating = doc.select("ul.average-rating > li > p[data-value]").toString().toRatingInt()
        val actors =
            doc.select("div.professionals > div").map {
                ActorData(
                    Actor(
                        it.select("div.prof > p").text().toString(),
                        "https:" + it.select("div.imgwrap img").attr("src").toString()
                    ),
                    roleString = it.select("div.prof > label").text().toString(),
                )
            }
        

        // val mp4link = doc.select("video#play_html5_api").attr("src")
        // Extracting the script content
        // val scriptContent = doc.select("script").first().data()
 // Using JSoup-Extractor to extract the MP4 link
        // val mp4link = JsoupExtractor.extract(scriptContent).get("src").asString()
 // val mp4link = doc.selectFirst("video#play")?.attr("src")
        // doc.select("video#play").attr("src")
        // Extracting the script content
        // val scriptContent = doc.selectFirst("script:containsData('video/mp4')")?.data() ?: ""
 // Using regex to extract the MP4 link
        // val mp4linkRegex = Regex("'src': '(.*?)'")
        // val matchResult = mp4linkRegex.find(scriptContent)
 // Extracting the MP4 link from the match result
        // val mp4link = matchResult?.groupValues?.get(1)
        // val scripts = doc.select("script")
        // var mp4link: String? = null
        // for (script in scripts) {
        //    val data = script.data()
        //    if (data.contains("'sources': [")) {
        //        mp4link = Regex("'src': '(.*?)'").find(data)?.groupValues?.get(1)
        //        break
        //    }
        // }
        // val mp4link = doc.selectFirst("script:containsData('sources: [')")?.data()?.let { Regex("'src': '(.*?)'").find(it)?.groupValues?.get(1) }
        // val scriptContent = doc.select("script:containsData(video/mp4)").html()
        // val mp4Regex = Regex("""'src': '(.*?)'""")
        // val mp4Match = mp4Regex.find(scriptContent)
        // val mp4link = mp4Match?.groupValues?.get(1)
        val mp4LinkRegex = Regex("""http.*?\.mp4""")
        val mp4Link = mp4LinkRegex.find(doc.html())?.value
        // Get the script content containing the video sources
        // val scriptContent = doc.select("script:containsData(video/mp4)").html()
        // Define a regex pattern to match the src attribute within the sources array
        // val mp4Regex = Regex("""{ src: '([^']+)'.*type: 'video/mp4' }""")
        // Find the first match of the regex pattern in the script content
        // val mp4Match = mp4Regex.find(scriptContent)
        // Extract the MP4 link from the regex match
        // val mp4link = mp4Match?.groupValues?.get(1)
        val m3u8link = doc.select("#UIVideoPlayer").attr("data-hls-link")

        return newMovieLoadResponse(title, href, TvType.Movie, "$mp4link,$m3u8link") {
                this.posterUrl = poster?.trim()
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                this.actors = actors
            }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val mp4link = data.substringBefore(",")
        val m3u8link = data.substringAfter(",")

        safeApiCall {
            callback.invoke(
                ExtractorLink(
                    "$name-MP4",
                    "$name-MP4",
                    mp4link,
                    "$mainUrl/",
                    Qualities.Unknown.value,
                    false
                )
            )
            callback.invoke(
                ExtractorLink(
                    "$name-M3U8",
                    "$name-M3U8",
                    m3u8link,
                    "$mainUrl/",
                    Qualities.Unknown.value,
                    true
                )
            )
        }

        return true
    }
}
