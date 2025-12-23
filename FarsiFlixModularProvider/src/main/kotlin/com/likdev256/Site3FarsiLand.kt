package com.likdev256

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Site 3: FarsiLand (farsiland.com)
 * Handles movies and TV series from FarsiLand.
 */
class Site3FarsiLand(override val api: MainAPI) : SiteHandler {
    override val siteUrl = "https://farsiland.com"
    override val siteName = "FarsiLand"
    
    override val mainPages = listOf(
        "$siteUrl/iran-movie-2025/" to "Movies - $siteName",
        "$siteUrl/series-26/" to "Series - $siteName",
    )
    
    override fun getHomeSelector(url: String): String = "article.item"
    
    override fun parseHomeItem(element: Element): SearchResponse? {
        val title = element.selectFirst("div.data h3 a")?.text()?.trim() ?: return null
        val href = element.selectFirst("div.data h3 a")?.attr("href")?.let { fixUrl(it) } ?: return null
        val posterUrl = element.selectFirst("div.poster img")?.attr("src")?.trim()?.let { fixUrlNull(it) }
        val type = if (element.hasClass("tvshows")) TvType.TvSeries else TvType.Movie
        return with(api) {
            newMovieSearchResponse(title, href, type) {
                this.posterUrl = posterUrl
            }
        }
    }
    
    fun parseSearchItem(element: Element): SearchResponse? {
        val titleElement = element.selectFirst("div.details div.title a")
        val title = titleElement?.text()?.trim() ?: return null
        val href = fixUrl(titleElement.attr("href"))
        val posterUrl = fixUrlNull(element.selectFirst("div.image img")?.attr("src"))
        val type = if (element.hasClass("tvshows")) TvType.TvSeries else TvType.Movie
        return with(api) {
            newMovieSearchResponse(title, href, type) {
                this.posterUrl = posterUrl
            }
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
        val isTvSeries = url.contains("/tvshows/")
        val isMovie = url.contains("/movies/")
        val isEpisode = url.contains("/episodes/")

        return with(api) {
            when {
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
                isMovie || isEpisode -> {
                    val title = if (isEpisode) {
                        document.selectFirst("div#info h2")?.text()?.trim()
                    } else {
                        document.selectFirst("div.data h2")?.text()?.trim()
                    } ?: return null
                    
                    val poster = if (isEpisode) {
                        fixUrlNull(document.selectFirst("#fakeplayer .playbox img.cover")?.attr("src"))
                    } else {
                        fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
                    }
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
    }
    
    override suspend fun loadLinks(
        data: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val document = app.get(data).document
            val formAction = document.selectFirst("form#watch")?.attr("action") ?: return false
            val formId = document.selectFirst("form#watch input[name=id]")?.attr("value") ?: return false

            val redirectPage = app.post(formAction, data = mapOf("id" to formId)).document
            val nextFormAction = redirectPage.selectFirst("form#watch")?.attr("action") ?: return false
            val postId = redirectPage.selectFirst("form#watch input[name=postid]")?.attr("value") ?: return false

            val finalPage = app.post(nextFormAction, data = mapOf("postid" to postId)).document
            val qualityForms = finalPage.select("form[id^=watch]")

            if (qualityForms.isNotEmpty()) {
                var foundAny = false
                qualityForms.forEach { form ->
                    val q = form.selectFirst("input[name=q]")?.attr("value")?.toIntOrNull() ?: return@forEach
                    val action = fixUrl(form.attr("action"))
                    val postIdForRedirect = form.selectFirst("input[name=postid]")?.attr("value") ?: return@forEach

                    try {
                        val finalRedirectPage = app.post(
                            action,
                            data = mapOf("q" to q.toString(), "postid" to postIdForRedirect)
                        ).document

                        val mp4Link = extractMp4Link(finalRedirectPage)
                        if (mp4Link.isNotBlank()) {
                            val quality = when (q) {
                                1080 -> Qualities.P1080
                                720 -> Qualities.P720
                                480 -> Qualities.P480
                                else -> Qualities.Unknown
                            }

                            callback.invoke(
                                newExtractorLink(
                                    source = siteName,
                                    name = "$siteName ${q}p",
                                    url = mp4Link
                                ).apply {
                                    this.quality = quality.value
                                    this.referer = action
                                    this.headers = mapOf("Referer" to action)
                                }
                            )
                            foundAny = true
                        }
                    } catch (_: Exception) {}
                }
                foundAny
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }
    
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
                val mp4Pattern = """file:\s*['"]([^'"]+)['"]""".toRegex()
                val matchResult = mp4Pattern.find(scriptContent)
                if (matchResult != null) {
                    return matchResult.groupValues[1]
                }
            }
        }
        return ""
    }
    
    private fun fixUrl(url: String): String {
        return if (url.startsWith("http")) url else "$siteUrl$url"
    }
    
    private fun fixUrlNull(url: String?): String? {
        return url?.let { if (it.startsWith("http")) it else "$siteUrl$it" }
    }
}
