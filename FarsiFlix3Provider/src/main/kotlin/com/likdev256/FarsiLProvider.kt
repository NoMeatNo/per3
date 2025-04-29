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

class FarsiFlix3Provider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://Farsiland.com"
    override var name = "Farsi Flix #3"
    override val hasMainPage = true
    override var lang = "fa"
    override val hasDownloadSupport = true
    override var sequentialMainPage = true
    override var sequentialMainPageDelay: Long = 100
    override val supportedTypes = setOf(
        TvType.Movie, TvType.TvSeries, TvType.Live
    )

    override val mainPage = mainPageOf(
        "$mainUrl/iranian-movies-2025/" to "Movies",
        "$mainUrl/series-22/" to "Last Series",
        "$mainUrl/iranian-series/" to "Old Series",
        "$mainUrl/old-iranian-movies/" to "Old Movies",
        "$mainUrl/episodes-12/" to "Last Episodes",
        "$mainUrl/live-tv/category/iran.html" to "Live TVs",        
    )

override suspend fun getMainPage(
    page: Int, 
    request: MainPageRequest
): HomePageResponse {
    val link = when (request.name) {
        "Movies" -> "$mainUrl/iranian-movies-2025/"
        "Last Series" -> "$mainUrl/series-22/"
        "Old Series" -> "$mainUrl/iranian-series/"
        "Old Movies" -> "$mainUrl/old-iranian-movies/"
        "Last Episodes" -> "$mainUrl/episodes-12/"
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
        val resultFarsiFlix3 = app.get("$mainUrl/search/$fixedQuery")
            .document.select("div.result-item")
            .mapNotNull { it.toParseSearchResult() }

        return resultFarsiFlix3.sortedBy { -FuzzySearch.partialRatio(it.name.replace("(\\()+(.*)+(\\))".toRegex(), "").lowercase(), query.lowercase()) }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val isTvSeries = url.contains("/tvshows/")
        val isMovie = url.contains("/movies/")
        val isEpisode = url.contains("/episodes/")

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
        val title = document.selectFirst("div.data h2")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
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
        println("Loading links for: $data")
        
        // Step 1: Get initial page with referer
        val document = app.get(data, referer = mainUrl).document
        val formAction = document.selectFirst("form#watch")?.attr("href") 
            ?: document.selectFirst("form#watch")?.attr("action")
            ?: return false.also { println("No form action found") }
        
        val formId = document.selectFirst("form#watch input[name=id]")?.attr("value") 
            ?: return false.also { println("No form ID found") }

        // Step 2: First form submission
        val redirectPage = app.post(
            formAction, 
            data = mapOf("id" to formId),
            referer = data
        ).document
        
        val nextFormAction = redirectPage.selectFirst("form#watch")?.attr("action") 
            ?: return false.also { println("No second form action found") }
        val postId = redirectPage.selectFirst("form#watch input[name=postid]")?.attr("value") 
            ?: return false.also { println("No post ID found") }

        // Step 3: Second form submission
        val finalPage = app.post(
            nextFormAction, 
            data = mapOf("postid" to postId),
            referer = formAction
        ).document
        
        val qualityForms = finalPage.select("form[id^=watch]")
        if (qualityForms.isEmpty()) {
            println("No quality forms found")
            return false
        }

        var foundAny = false
        qualityForms.forEach { form ->
            try {
                val q = form.selectFirst("input[name=q]")?.attr("value")?.toIntOrNull() 
                    ?: return@forEach.also { println("No quality value found in form") }
                val action = form.attr("action") 
                    ?: return@forEach.also { println("No form action found") }
                val postIdForRedirect = form.selectFirst("input[name=postid]")?.attr("value") 
                    ?: return@forEach.also { println("No post ID for redirect found") }

                println("Processing quality: ${q}p")
                
                // Step 4: Final form submission for quality
                val finalRedirectPage = app.post(
                    action,
                    data = mapOf("q" to q.toString(), "postid" to postIdForRedirect),
                    referer = nextFormAction
                ).document

                val mp4Link = extractMp4Link(finalRedirectPage).takeIf { it.isNotBlank() }
                    ?: return@forEach.also { println("No MP4 link found for ${q}p") }

                println("Found video link for ${q}p: $mp4Link")
                
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = "${this.name} ${q}p",
                        url = mp4Link,
                        referer = action
                    ).apply {
                        this.quality = when (q) {
                            in 1080..Int.MAX_VALUE -> Qualities.P1080.value
                            in 720..1079 -> Qualities.P720.value
                            in 480..719 -> Qualities.P480.value
                            else -> Qualities.Unknown.value
                        }
                        this.headers = mapOf(
                            "Referer" to action,
                            "Origin" to mainUrl
                        )
                    }
                )
                foundAny = true
            } catch (e: Exception) {
                println("Error processing quality form: ${e.message}\n${e.stackTraceToString()}")
            }
        }
        
        foundAny
    } catch (e: Exception) {
        println("Error in loadLinks: ${e.message}\n${e.stackTraceToString()}")
        false
    }
}

// Function to extract MP4 link remains the same
private fun extractMp4Link(page: Document): String {
    // Check if there is a video element first
    val mp4Link = page.select("video.jw-video").attr("src")
    if (mp4Link.isNotBlank()) {
        return mp4Link
    }
    // If the MP4 link was not found in the video element, look for the script
    page.select("script").forEach { scriptElement ->
        val scriptContent = scriptElement.html()
        if (scriptContent.contains("sources: [")) {
            // Extract the MP4 link from the script
            val mp4Pattern = """file:\s*['"]([^'"]+)['"]""".toRegex()
            val matchResult = mp4Pattern.find(scriptContent)
            if (matchResult != null) {
                return matchResult.groupValues[1]
            }
        }
    }
    return ""
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
