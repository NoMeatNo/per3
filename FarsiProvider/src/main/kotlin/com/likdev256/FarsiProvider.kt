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
        "$mainUrl/new-iranian-movies-1/" to "Movies",
        "$mainUrl/series-21/" to "Last Series",
        "$mainUrl/tv-series-old/" to "Old Series",
        "$mainUrl/old-movies/" to "Old Movies",
        "$mainUrl/episodes-10/" to "Last Episodes",
        "$mainUrl/live-tv/category/iran.html" to "Live TVs",        
    )

override suspend fun getMainPage(
    page: Int, 
    request: MainPageRequest
): HomePageResponse {
    val link = when (request.name) {
        "Movies" -> "$mainUrl/new-iranian-movies-1/"
        "Last Series" -> "$mainUrl/series-21/"
        "Old Series" -> "$mainUrl/tv-series-old/"
        "Old Movies" -> "$mainUrl/old-movies/"
        "Last Episodes" -> "$mainUrl/episodes-10/"
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
        this@FarsiProvider.name,
        TvType.Live,
        fixUrlNull(this.select("img").attr("data-src") ?: this.select("img").attr("src")),
    )
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

    override suspend fun search(query: String): List<SearchResponse>? {
        val fixedQuery = query.replace(" ", "+")
        val resultFarsi = app.get("$mainUrl/search/$fixedQuery")
            .document.select("div.result-item")
            .mapNotNull { it.toSearchResult() }

        return resultFarsi.sortedBy { -FuzzySearch.partialRatio(it.name.replace("(\\()+(.*)+(\\))".toRegex(), "").lowercase(), query.lowercase()) }
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
        // Step 1: Get the initial document
        val document = app.get(data).document
        // Extract the form action URL and id
        val formAction = document.selectFirst("form#watch")?.attr("action") ?: return false
        val formId = document.selectFirst("form#watch input[name=id]")?.attr("value") ?: return false
        // Step 2: Submit the form and get the redirect page
        val redirectPage = app.post(
            formAction,
            data = mapOf("id" to formId)
        ).document
        // Step 3: Extract the next form action and submit it
        val nextFormAction = redirectPage.selectFirst("form#watch")?.attr("action") ?: return false
        val postId = redirectPage.selectFirst("form#watch input[name=postid]")?.attr("value") ?: return false
        // Submit the next form and get the final page
        val finalPage = app.post(
            nextFormAction,
            data = mapOf("postid" to postId)
        ).document
        // Step 4 & 5: Check for forms that redirect to another URL
        val qualityForms = finalPage.select("form[id^=watch]")
        if (qualityForms.isNotEmpty()) {
            val availableQualities = qualityForms.mapNotNull { form ->
                form.selectFirst("input[name=q]")?.attr("value")?.toIntOrNull()
            }
            
            val preferredQuality = when {
                720 in availableQualities -> 720
                1080 in availableQualities -> 1080
                480 in availableQualities -> 480
                else -> availableQualities.firstOrNull() ?: return false
            }
            
            val selectedForm = qualityForms.first { form ->
                form.selectFirst("input[name=q]")?.attr("value")?.toIntOrNull() == preferredQuality
            }
            
            val redirectFormAction = selectedForm.attr("action")
            val postIdForNextRedirect = selectedForm.selectFirst("input[name=postid]")?.attr("value") ?: return false
            
            // Submit the form to the new URL
            val finalRedirectPage = app.post(
                redirectFormAction,
                data = mapOf("q" to preferredQuality.toString(), "postid" to postIdForNextRedirect)
            ).document
            
            // Extract the MP4 link from the final redirect page
            val finalMp4Link = extractMp4Link(finalRedirectPage)
            if (finalMp4Link.isNotBlank()) {
                val qualityEnum = when (preferredQuality) {
                    1080 -> Qualities.P1080
                    720 -> Qualities.P720
                    480 -> Qualities.P480
                    else -> Qualities.P720
                }
                callback.invoke(
                    ExtractorLink(
                        this.name,
                        this.name,
                        finalMp4Link,
                        referer = data,
                        quality = qualityEnum.value
                    )
                )
                true
            } else {
                false
            }
        } else {
            false
        }
    } catch (e: Exception) {
        // Instead of logging, we'll just return false to indicate failure
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
