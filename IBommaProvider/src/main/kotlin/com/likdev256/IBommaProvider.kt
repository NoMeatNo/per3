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

    //pages
    //"#content > div > article" //Latest
    //"#content > article:lt(6)" //web series
    //"#content > article:gt(5):lt(6)" //foreign dub
    //"#content > article:gt(11)" //addon

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
       // val link = "$mainUrl/saff1"
        val document = app.get(link).document
        val home = document.select("div.col-md-2.col-sm-3.col-xs-6").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }
      //  val pageSelectors = listOf(
      //      Pair("Latest", "#content > div > article"),
      //      Pair("Movies", "#content > article:nth-child(-n+13)"),
      //  )
      //  val pages = pageSelectors.apmap { (title, selector) ->
      //      val list = document.select(selector).mapNotNull {
      //              it.toSearchResult()
      //          }
      //      HomePageList(title, list)
      //  }
      //  return HomePageResponse(pages)
  //  }

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
//        val searchUrl = app.get(mainUrl).document.select(".mob-search form").attr("action")
//        fun String.encodeUri() = URLEncoder.encode(this, "utf8")
//        val document = app.get("$searchUrl?label=telugu&q=${query.encodeUri()}").document
//        val scriptData = document.select("script").find { it.data().contains("data=") }?.data()
//            ?.substringAfter("data= ")?.substringBefore("</script>") ?: return null
//        val response = parseJson<Response>(scriptData)
//        return response.hits?.hitslist?.map {
//            val title = it.source?.title?.substringBefore("Movie") ?: return null
//            val posterUrl = it.source?.imageLink ?: return null
//            val href = it.source?.location ?: return null
//            newMovieSearchResponse(title, href, TvType.Movie) {
 //               this.posterUrl = posterUrl
//            }
//        }
//    }

    override suspend fun load(url: String): LoadResponse? {

        val document = app.get(url).document
        val title = document.selectFirst("div.col-sm-9 p.m-t-10 strong")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("video#play")?.attr("poster"))
 //       val year = document.select(".entry-tags-movies span").text().trim().toIntOrNull()
      //  val tvType =
      //      if (document.select("#eplist").isNullOrEmpty()) TvType.Movie else TvType.TvSeries
      //  val tvType = if (document.select(".col-md-12.col-sm-12:contains(Upcomming Movies)").isNotEmpty()) TvType.TvSeries else TvType.Movie
      //  val tvType = if (document.select(".col-md-12.col-sm-12:has(div.owl-carousel[class^=season_])").isNotEmpty()) TvType.TvSeries else TvType.Movie
        val tvType = if (document.select(".col-md-12.col-sm-12:has(div.owl-carousel)").isNotEmpty()) TvType.TvSeries else TvType.Movie
  //      val description = document.select("a.btn-tags").map { it.text() }
       //     document.selectFirst(".additional-info")?.text()?.trim()!!.replace("Synopsis: ", "")
      //  val trailer = fixUrlNull(document.select(".button-trailer a").attr("src"))
        //val rating = document.select("div.gmr-meta-rating > span:nth-child(3)").text().toRatingInt()
      //  val actors =
      //      document.select("div.clearfix.content-moviedata > div:nth-child(7) a").map { it.text() }

        val seasonNumbers = document.select(".col-md-12.col-sm-12:has(.owl-carousel) .owl-carousel").map { carousel ->
            val seasonNumber = carousel.attr("class")
                .replace("owl-carousel season_", "")
                .trim()
            seasonNumber
        }
  //      val seasonNumber = document.select(".col-md-12.col-sm-12:has(.owl-carousel) .owl-carousel").attr("class")
  //          .replace("owl-carousel season_", "")
  //          .trim()
        return if (tvType == TvType.TvSeries) {
           // val episodes = document.select(".owl-carousel.season_$seasonNumber .item").mapNotNull { item ->
            val episodes = seasonNumbers.flatMap { seasonNumber ->
                document.select(".owl-carousel.season_$seasonNumber .item").mapNotNull { item ->
                    val seasonName = "Season $seasonNumber"
                    val figcaption = item.select(".figure figcaption").text().trim()
                    val episode = figcaption.filter { it.isDigit() }.toIntOrNull()
                 //   val name = "${item.select(".figure figcaption").text().trim()} - $seasonName"
                   // val name = figcaption
                    val name = if (episode != null) {
                        "${figcaption} - $seasonName"
                    } else {
                        figcaption
                    }
                    val href = fixUrlNull(item.select("a").attr("href"))
                    if (href != null) {
                        Episode(data = href, name = name, episode = episode)
                    } else {
                        null
                    }
                }
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
  //              this.year = year
 //               this.plot = description
//                rating
//                addActors(actors)
//                addTrailer(trailer)
            }

        //    val episodeUrls = getUrls(url) ?: return null
        //    val episodes = document.select("#eplist tr").mapNotNull { res ->
        //        val name = res.select("b").text().trim()
                //val season = name.substringAfter("S").substringBefore(' ').toInt()
        //        val episode = res.select("button").text().filter { it.isDigit() }.toInt()
          //      val href = episodeUrls[episode]
           //     Episode(
            //        data = href, name = name, episode = episode
           //     )
          //  }

//            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
//                this.posterUrl = poster
//                this.year = year
//                this.plot = description
//                rating
//                addActors(actors)
//                addTrailer(trailer)
//            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
//                this.year = year
 //               this.plot = description
                //this.tags = tags
//                rating
//                addActors(actors)
//                addTrailer(trailer)
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
