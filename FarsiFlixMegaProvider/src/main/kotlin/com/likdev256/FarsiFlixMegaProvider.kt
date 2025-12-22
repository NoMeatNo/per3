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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class FarsiFlixMegaProvider : MainAPI() {
    override var mainUrl = "https://farsiplex.com"
    override var name = "FarsiFlix Mega"
    override val hasMainPage = true
    override var lang = "fa"
    override val hasDownloadSupport = true
    override var sequentialMainPage = true
    override var sequentialMainPageDelay: Long = 100
    override val supportedTypes = setOf(
        TvType.Movie, TvType.TvSeries
    )

    // Site configurations
    companion object {
        const val SITE1_URL = "https://www.diycraftsguide.com"
        const val SITE1_NAME = "DiyGuide"
        
        const val SITE2_URL = "https://farsiplex.com"
        const val SITE2_NAME = "FarsiPlex"
        
        const val SITE3_URL = "https://farsiland.com"
        const val SITE3_NAME = "FarsiLand"

        const val SITE4_URL = "https://www.irantamasha.com"
        const val SITE4_NAME = "IranTamasha"

        const val SITE5_URL = "https://persianhive.com"
        const val SITE5_NAME = "PersianHive"
    }

    override val mainPage = mainPageOf(
        // Site 1 - DiyGuide
        "$SITE1_URL/movies.html" to "Movies - $SITE1_NAME",
        "$SITE1_URL/tv-series.html" to "Series - $SITE1_NAME",
        // Site 2 - FarsiPlex
        "$SITE2_URL/movie/" to "Movies - $SITE2_NAME",
        "$SITE2_URL/tvshow/" to "Series - $SITE2_NAME",
        // Site 3 - FarsiLand
        "$SITE3_URL/iran-movie-2025/" to "Movies - $SITE3_NAME",
        "$SITE3_URL/series-26/" to "Series - $SITE3_NAME",
        "$SITE3_URL/iranian-series/" to "Old Series - $SITE3_NAME",
        "$SITE3_URL/episodes-15/" to "Latest Episodes - $SITE3_NAME",
        // Site 4 - IranTamasha
        "$SITE4_URL/series/" to "Series - $SITE4_NAME",
        // Site 5 - PersianHive
        "$SITE5_URL/all-farsi-movies/" to "Movies - $SITE5_NAME",
        "$SITE5_URL/all-farsi-series/" to "Series - $SITE5_NAME",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data).document
        val home = when {
            // Site 1 selectors
            request.data.contains(SITE1_URL) -> {
                document.select("div.col-md-2.col-sm-3.col-xs-6").mapNotNull { it.toSite1SearchResult() }
            }
            // Site 4 selectors
            request.data.contains(SITE4_URL) -> {
                 document.select("article.post-item").mapNotNull { it.toSite4SearchResult() }
            }
            // Site 5 selectors
            request.data.contains(SITE5_URL) -> {
                document.select("div.pciwgas-post-cat-inner").mapNotNull { it.toSite5SearchResult() }
            }
            // Site 2 & 3 selectors (they use similar structure)
            else -> {
                document.select("article.item").mapNotNull { it.toSite23SearchResult() }
            }
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSite1SearchResult(): SearchResponse? {
        val title = this.selectFirst("div.movie-title h3 a")?.text()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("div.movie-title h3 a")?.attr("href").toString())
        val posterUrl = fixUrlNull(this.selectFirst("div.latest-movie-img-container")?.attr("data-src")?.trim())
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    private fun Element.toSite23SearchResult(): SearchResponse? {
        val title = this.selectFirst("div.data h3 a")?.text()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("div.data h3 a")?.attr("href").toString())
        val posterUrl = fixUrlNull(this.selectFirst("div.poster img")?.attr("src")?.trim())
        val type = if (this.hasClass("tvshows")) TvType.TvSeries else TvType.Movie
        return newMovieSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
        }
    }

    private fun Element.toSite4SearchResult(): SearchResponse? {
        val title = this.selectFirst("h3.entry-title a")?.text()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("h3.entry-title a")?.attr("href").toString())
        val posterUrl = fixUrlNull(this.selectFirst("div.blog-pic img")?.attr("src")?.trim() ?:
                                 this.selectFirst("div.blog-pic img")?.attr("data-src")?.trim())
        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
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

    private fun Element.toSite5SearchResult(): SearchResponse? {
        val title = this.selectFirst("div.pciwgas-title a, div.pciw-title a")?.text()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("a.pciwgas-hover, a.pciw-hover")?.attr("href") ?: return null)
        val posterUrl = fixUrlNull(this.selectFirst("img.pciwgas-img, img.pciw-img")?.attr("src"))
        val type = if (href.contains("movie") || href.contains("film")) TvType.Movie else TvType.TvSeries

        return if (type == TvType.Movie) {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        } else {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val fixedQuery = query.replace(" ", "+")
        val allResults = mutableListOf<SearchResponse>()

        // Search all 3 sites in parallel
        coroutineScope {
            val site1Deferred = async {
                try {
                    app.get("$SITE1_URL/search?q=$fixedQuery")
                        .document.select("div.col-md-2.col-sm-3.col-xs-6")
                        .mapNotNull { it.toSite1SearchResult() }
                } catch (e: Exception) { emptyList() }
            }
            
            val site2Deferred = async {
                try {
                    app.get("$SITE2_URL/?s=$fixedQuery")
                        .document.select("div.result-item")
                        .mapNotNull { it.toParseSearchResult() }
                } catch (e: Exception) { emptyList() }
            }
            
            val site3Deferred = async {
                try {
                    app.get("$SITE3_URL/?s=$fixedQuery")
                        .document.select("div.result-item")
                        .mapNotNull { it.toParseSearchResult() }
                } catch (e: Exception) { emptyList() }
            }

            allResults.addAll(site1Deferred.await())
            allResults.addAll(site2Deferred.await())
            allResults.addAll(site3Deferred.await())

             val site4Deferred = async {
                try {
                    app.get("$SITE4_URL/?s=$fixedQuery")
                        .document.select("article.post-item")
                        .mapNotNull { it.toSite4SearchResult() }
                } catch (e: Exception) { emptyList() }
            }
            allResults.addAll(site4Deferred.await())

            // Site 5 - PersianHive (behind Cloudflare, might need special handling)
            val site5Deferred = async {
                try {
                    app.get("$SITE5_URL/?s=$fixedQuery")
                        .document.select("div.pciwgas-post-cat-inner")
                        .mapNotNull { it.toSite5SearchResult() }
                } catch (e: Exception) { emptyList() }
            }
            allResults.addAll(site5Deferred.await())
        }

        return allResults.sortedBy { 
            -FuzzySearch.partialRatio(
                it.name.replace("(\\()+(.*)+(\\))".toRegex(), "").lowercase(), 
                query.lowercase()
            ) 
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        return when {
            // Site 1 - DiyGuide
            url.contains(SITE1_URL) -> loadSite1(url, document)
            // Site 2 - FarsiPlex
            url.contains(SITE2_URL) -> loadSite2(url, document)
            // Site 3 - FarsiLand
            url.contains(SITE3_URL) -> loadSite3(url, document)
            // Site 4 - IranTamasha
            url.contains(SITE4_URL) -> loadSite4(url, document)
            // Site 5 - PersianHive
            url.contains(SITE5_URL) -> loadSite5(url, document)
            else -> null
        }
    }

    private suspend fun loadSite1(url: String, document: Document): LoadResponse? {
        val data = document.select("script").find { it.data().contains("var channelName =") }?.data()
        val title = document.selectFirst("div.col-sm-9 p.m-t-10 strong")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("video#play")?.attr("poster"))
        val isTvSeries = document.select(".col-md-12.col-sm-12:has(div.owl-carousel)").isNotEmpty()

        return if (isTvSeries) {
            val episodes = mutableListOf<Episode>()
            val rows = document.select(".row")
            var seasonNumber = 0
            
            rows.forEachIndexed { _, row ->
                if (row.select(".movie-heading").isNotEmpty()) {
                    val seasonText = row.select(".movie-heading span").text().trim()
                    seasonNumber = seasonText.removePrefix("Season").trim().toIntOrNull() ?: 0
                } else if (row.select(".owl-carousel").isNotEmpty() && seasonNumber > 0) {
                    row.select(".item").forEach { item ->
                        val episodeLink = item.select("a").attr("href")
                        val episodeNumberText = item.select(".figure-caption").text().trim()
                        val episodeNumber = episodeNumberText.removePrefix("E").toIntOrNull() ?: 0
                        val episodeName = "Season $seasonNumber Episode $episodeNumber"
                        if (episodeLink.isNotEmpty()) {
                            episodes.add(newEpisode(episodeLink) {
                                name = episodeName
                                season = seasonNumber
                                episode = episodeNumber
                            })
                        }
                    }
                }
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

    private suspend fun loadSite2(url: String, document: Document): LoadResponse? {
        val isTvSeries = url.contains("/tvshow/")
        val isMovie = url.contains("/movie/")
        val isEpisode = url.contains("/episode/")

        return when {
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
            isMovie -> {
                val title = document.selectFirst("div.data h1")?.text()?.trim() ?: return null
                val poster = fixUrlNull(document.selectFirst("div.playbox img.cover")?.attr("src"))
                val plot = document.selectFirst("div#info div.wp-content p")?.text()?.trim()

                newMovieLoadResponse(title, url, TvType.Movie, url) {
                    this.posterUrl = poster
                    this.plot = plot
                }
            }
            isEpisode -> {
                val title = document.selectFirst("div#info h2")?.text()?.trim() ?: return null
                val poster = fixUrlNull(document.selectFirst("#fakeplayer .playbox img.cover")?.attr("src"))
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

    private suspend fun loadSite3(url: String, document: Document): LoadResponse? {
        val isTvSeries = url.contains("/tvshows/")
        val isMovie = url.contains("/movies/")
        val isEpisode = url.contains("/episodes/")

        return when {
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

    private suspend fun loadSite4(url: String, document: Document): LoadResponse? {
        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: 
                    document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.blog-pic img")?.attr("src") ?: 
                              document.selectFirst("meta[property=og:image]")?.attr("content"))
        val plot = document.selectFirst("div.entry-content p")?.text()?.trim()

        // Check if it is a series page (list of episodes)
        val episodeElements = document.select("article.post-item")
        
        return if (episodeElements.isNotEmpty() && !url.contains("-0") && !url.contains("-1")) {
            // Likely a series page listing episodes
            val episodes = episodeElements.mapNotNull {
                val epTitle = it.selectFirst("h3.entry-title a")?.text()?.trim() ?: return@mapNotNull null
                val epUrl = fixUrl(it.selectFirst("h3.entry-title a")?.attr("href") ?: return@mapNotNull null)
                val epPoster = fixUrlNull(it.selectFirst("img")?.attr("src"))
                
                 // Try to extract episode number from title (e.g. "Close Friend – 02")
                val epNumber = Regex("""\d+$""").find(epTitle)?.value?.toIntOrNull() ?: 
                               Regex(""" (\d+)""").findAll(epTitle).lastOrNull()?.value?.trim()?.toIntOrNull() ?: 0

                newEpisode(epUrl) {
                    name = epTitle
                    episode = epNumber
                    posterUrl = epPoster
                }
            }.reversed() // Usually listed newest first

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
            }
        } else {
            // Likely a single episode or movie page
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
            }
        }
    }

    private suspend fun loadSite5(url: String, document: Document): LoadResponse? {
        val title = document.selectFirst("h1.pciwgas-title, h1.pciw-title, h1.entry-title, h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("img.pciwgas-img, img.pciw-img")?.attr("src") ?: 
                              document.selectFirst("meta[property=og:image]")?.attr("content"))
        val description = document.selectFirst("div.pciwgas-desc p, div.pciw-desc p, div.entry-content p")?.text()?.trim()
        
        val episodeElements = document.select("article.post-item, div.pciwgas-post-cat-inner")
        val episodes = episodeElements.mapNotNull {
             val epTitle = it.selectFirst("a.pciwgas-hover, h2 a, h3 a")?.text() ?: return@mapNotNull null
             val epUrl = fixUrl(it.selectFirst("a.pciwgas-hover, a")?.attr("href") ?: return@mapNotNull null)
             newEpisode(epUrl) {
                 name = epTitle
             }
        }

        return if (episodes.isNotEmpty()) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
            }
        } else {
             newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var foundLinks = false

        // Load from the original source first
        when {
            data.contains(SITE1_URL) -> {
                if (loadLinksSite1(data, subtitleCallback, callback)) foundLinks = true
            }
            data.contains(SITE2_URL) -> {
                if (loadLinksSite2(data, subtitleCallback, callback)) foundLinks = true
            }
            data.contains(SITE3_URL) -> {
                if (loadLinksSite3(data, subtitleCallback, callback)) foundLinks = true
            }
            data.contains(SITE4_URL) -> {
                if (loadLinksSite4(data, subtitleCallback, callback)) foundLinks = true
            }
            data.contains(SITE5_URL) -> {
                if (loadLinksSite5(data, subtitleCallback, callback)) foundLinks = true
            }
        }

        // Additionally, try to find the same content on other sites
        // Extract the title to search on other sites
        try {
            val document = app.get(data).document
            val titleForSearch = extractTitleFromPage(data, document)
            
            if (titleForSearch != null && titleForSearch.length > 2) {
                coroutineScope {
                    // Search remaining sites for the same title
                    if (!data.contains(SITE1_URL)) {
                        async {
                            try {
                                val searchResults = app.get("$SITE1_URL/search?q=${titleForSearch.replace(" ", "+")}")
                                    .document.select("div.col-md-2.col-sm-3.col-xs-6")
                                    .mapNotNull { it.toSite1SearchResult() }
                                
                                searchResults.firstOrNull { 
                                    FuzzySearch.ratio(it.name.lowercase(), titleForSearch.lowercase()) > 80 
                                }?.let { match ->
                                    loadLinksSite1(match.url, subtitleCallback, callback)
                                }
                            } catch (_: Exception) {}
                        }
                    }
                    
                    if (!data.contains(SITE2_URL)) {
                        async {
                            try {
                                val searchResults = app.get("$SITE2_URL/?s=${titleForSearch.replace(" ", "+")}")
                                    .document.select("div.result-item")
                                    .mapNotNull { it.toParseSearchResult() }
                                
                                searchResults.firstOrNull { 
                                    FuzzySearch.ratio(it.name.lowercase(), titleForSearch.lowercase()) > 80 
                                }?.let { match ->
                                    loadLinksSite2(match.url, subtitleCallback, callback)
                                }
                            } catch (_: Exception) {}
                        }
                    }
                    
                    if (!data.contains(SITE3_URL)) {
                        async {
                            try {
                                val searchResults = app.get("$SITE3_URL/?s=${titleForSearch.replace(" ", "+")}")
                                    .document.select("div.result-item")
                                    .mapNotNull { it.toParseSearchResult() }
                                
                                searchResults.firstOrNull { 
                                    FuzzySearch.ratio(it.name.lowercase(), titleForSearch.lowercase()) > 80 
                                }?.let { match ->
                                    loadLinksSite3(match.url, subtitleCallback, callback)
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return foundLinks
    }

    private fun extractTitleFromPage(url: String, document: Document): String? {
        return when {
            url.contains(SITE1_URL) -> document.selectFirst("div.col-sm-9 p.m-t-10 strong")?.text()?.trim()
            url.contains(SITE2_URL) -> document.selectFirst("div.data h1")?.text()?.trim()
                ?: document.selectFirst("div#info h2")?.text()?.trim()
            url.contains(SITE3_URL) -> document.selectFirst("div.data h1")?.text()?.trim()
                ?: document.selectFirst("div#info h2")?.text()?.trim()
                ?: document.selectFirst("div.data h2")?.text()?.trim()
            else -> null
        }
    }

    // Site 1 link extraction (DiyGuide)
    private suspend fun loadLinksSite1(
        data: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var foundLinks = false
        data.split(",").forEach { url ->
            try {
                val document = app.get(url.trim()).document
                
                // Extract MP4 links from scripts
                val scriptContent = document.selectFirst("script:containsData('video/mp4')")?.data() ?: ""
                val mp4LinkRegex = Regex("""src: '(https?://[^']+\.mp4)'""")
                mp4LinkRegex.findAll(scriptContent).forEach { matchResult ->
                    if (matchResult.groupValues.size > 1) {
                        val mp4Link = matchResult.groupValues[1]
                        if (mp4Link.isNotBlank()) {
                            callback.invoke(
                                newExtractorLink(
                                    source = "$SITE1_NAME",
                                    name = "$SITE1_NAME",
                                    url = mp4Link
                                ).apply {
                                    this.quality = Qualities.Unknown.value
                                }
                            )
                            foundLinks = true
                        }
                    }
                }
                
                // Extract ok.ru / VK embedded videos from iframes
                document.select("iframe").forEach { iframe ->
                    val iframeSrc = iframe.attr("src")
                    if (iframeSrc.isNotBlank()) {
                        if (iframeSrc.contains("ok.ru") || iframeSrc.contains("vk.com")) {
                            loadExtractor(iframeSrc, SITE1_URL, subtitleCallback, callback)
                            foundLinks = true
                        }
                    }
                }
                
                // Also check for video-embed-container divs with iframes
                document.select("div.video-embed-container iframe").forEach { iframe ->
                    val iframeSrc = iframe.attr("src")
                    if (iframeSrc.isNotBlank() && (iframeSrc.contains("ok.ru") || iframeSrc.contains("vk.com"))) {
                        loadExtractor(iframeSrc, SITE1_URL, subtitleCallback, callback)
                        foundLinks = true
                    }
                }
            } catch (_: Exception) {}
        }
        return foundLinks
    }

    // Site 2 link extraction (FarsiPlex)
    private suspend fun loadLinksSite2(
        data: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val document = app.get(data).document
            var foundLinks = 0
            
            // Get post ID from the watch form
            val watchForm = document.selectFirst("form[id^=watch-]")
            val postId = watchForm?.selectFirst("input[name=id]")?.attr("value") ?: ""
            
            if (postId.isNotEmpty()) {
                // Try dooplayer API to get embed URLs for each quality
                for (nume in 1..3) {
                    try {
                        val apiUrl = "$SITE2_URL/wp-json/dooplayer/v2/$postId/tv/$nume"
                        val apiResponse = app.get(apiUrl).text
                        
                        val embedUrlRegex = Regex(""""embed_url"\s*:\s*"([^"]+)"""")
                        val embedMatch = embedUrlRegex.find(apiResponse)
                        
                        if (embedMatch != null) {
                            val embedUrl = embedMatch.groupValues[1].replace("\\/", "/")
                            
                            // Fetch the jwplayer page to get the REAL video URL with md5 token
                            if (embedUrl.contains("jwplayer")) {
                                try {
                                    val jwPlayerHtml = app.get(embedUrl).text
                                    
                                    // Extract the jw.file value which contains the real playable URL
                                    val jwFileRegex = Regex(""""file"\s*:\s*"([^"]+)"""")
                                    val jwFileMatch = jwFileRegex.find(jwPlayerHtml)
                                    
                                    if (jwFileMatch != null) {
                                        val realVideoUrl = jwFileMatch.groupValues[1].replace("\\/", "/")
                                        
                                        if (realVideoUrl.isNotBlank() && (realVideoUrl.contains(".mp4") || realVideoUrl.contains(".m3u8"))) {
                                            val quality = when {
                                                realVideoUrl.contains("1080") -> Qualities.P1080.value
                                                realVideoUrl.contains("720") -> Qualities.P720.value
                                                realVideoUrl.contains("480") -> Qualities.P480.value
                                                realVideoUrl.contains("360") -> Qualities.P360.value
                                                else -> Qualities.Unknown.value
                                            }
                                            
                                            val qualityLabel = when (quality) {
                                                Qualities.P1080.value -> "1080p"
                                                Qualities.P720.value -> "720p"
                                                Qualities.P480.value -> "480p"
                                                Qualities.P360.value -> "360p"
                                                else -> ""
                                            }
                                            
                                            callback.invoke(
                                                newExtractorLink(
                                                    source = SITE2_NAME,
                                                    name = "$SITE2_NAME $qualityLabel",
                                                    url = realVideoUrl
                                                ).apply {
                                                    this.quality = quality
                                                    this.referer = SITE2_URL
                                                }
                                            )
                                            foundLinks++
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
            
            // Fallback: try player options from page
            if (foundLinks == 0) {
                val playerDocument = if (watchForm != null) {
                    val formAction = watchForm.attr("action")
                    val formId = watchForm.selectFirst("input[name=id]")?.attr("value") ?: ""
                    if (formAction.isNotEmpty() && formId.isNotEmpty()) {
                        app.post(formAction, data = mapOf("id" to formId), referer = data).document
                    } else document
                } else document
                
                val dataPostId = playerDocument.selectFirst("li.dooplay_player_option[data-post]")?.attr("data-post")
                    ?: playerDocument.selectFirst("[data-post]")?.attr("data-post")
                
                if (dataPostId != null) {
                    val playerOptions = playerDocument.select("li.dooplay_player_option")
                    playerOptions.forEach { option ->
                        val nume = option.attr("data-nume")
                        val dataType = option.attr("data-type")
                        
                        try {
                            val apiUrl = "$SITE2_URL/wp-json/dooplayer/v2/$dataPostId/$dataType/$nume"
                            val apiResponse = app.get(apiUrl).text
                            
                            val embedUrlRegex = Regex(""""embed_url"\s*:\s*"([^"]+)"""")
                            val embedMatch = embedUrlRegex.find(apiResponse)
                            
                            if (embedMatch != null) {
                                val embedUrl = embedMatch.groupValues[1].replace("\\/", "/")
                                
                                if (embedUrl.contains("jwplayer")) {
                                    val jwPlayerHtml = app.get(embedUrl).text
                                    val jwFileRegex = Regex(""""file"\s*:\s*"([^"]+)"""")
                                    val jwFileMatch = jwFileRegex.find(jwPlayerHtml)
                                    
                                    if (jwFileMatch != null) {
                                        val realVideoUrl = jwFileMatch.groupValues[1].replace("\\/", "/")
                                        
                                        if (realVideoUrl.isNotBlank() && realVideoUrl.contains(".mp4")) {
                                            val quality = when {
                                                realVideoUrl.contains("1080") -> Qualities.P1080.value
                                                realVideoUrl.contains("720") -> Qualities.P720.value
                                                realVideoUrl.contains("480") -> Qualities.P480.value
                                                else -> Qualities.Unknown.value
                                            }
                                            
                                            callback.invoke(
                                                newExtractorLink(
                                                    source = SITE2_NAME,
                                                    name = SITE2_NAME,
                                                    url = realVideoUrl
                                                ).apply {
                                                    this.quality = quality
                                                    this.referer = SITE2_URL
                                                }
                                            )
                                            foundLinks++
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
            
            foundLinks > 0
        } catch (_: Exception) {
            false
        }
    }


    // Site 3 link extraction (FarsiLand)
    private suspend fun loadLinksSite3(
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

                        val mp4Link = extractMp4LinkSite3(finalRedirectPage)
                        if (mp4Link.isNotBlank()) {
                            val quality = when (q) {
                                1080 -> Qualities.P1080
                                720 -> Qualities.P720
                                480 -> Qualities.P480
                                else -> Qualities.Unknown
                            }

                            callback.invoke(
                                newExtractorLink(
                                    source = SITE3_NAME,
                                    name = "$SITE3_NAME ${q}p",
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

    private fun extractMp4LinkSite3(page: Document): String {
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

    // Site 4 link extraction (IranTamasha)
    private suspend fun loadLinksSite4(
        data: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val document = app.get(data).document
            var foundLinks = 0

            // 1. Extract from current page
            // Look for iframes
            document.select("iframe").forEach { iframe ->
                val src = iframe.attr("src")
                if (src.contains("ok.ru") || src.contains("vk.com") || src.contains("vkvideo.ru") || src.contains("closeload") || src.contains("youtube")) {
                    loadExtractor(src, data, subtitleCallback, callback)
                    foundLinks++
                }
            }
            
            // Look for Multi-Links using class "series-item" (Server 1, Link 1, Original Video, etc.)
            val serverLinks = document.select("a.series-item")
            
            serverLinks.forEach { link ->
                val serverUrl = fixUrl(link.attr("href"))
                if (serverUrl != data) { // Avoid infinite recursion if it's the same page
                    try {
                        val serverDoc = app.get(serverUrl).document
                         serverDoc.select("iframe").forEach { iframe ->
                            val src = iframe.attr("src")
                            if (src.isNotBlank()) {
                                // Handle goodstream.one directly
                                if (src.contains("goodstream.one")) {
                                    try {
                                        val embedHtml = app.get(src, headers = mapOf("Referer" to serverUrl)).text
                                        // Extract file URL from JWPlayer sources: [{file:"https://...m3u8?..."}]
                                        val fileRegex = Regex("""file\s*:\s*["']([^"']+)["']""")
                                        fileRegex.find(embedHtml)?.groupValues?.get(1)?.let { m3u8Url ->
                                            callback.invoke(
                                                newExtractorLink(
                                                    source = "GoodStream",
                                                    name = "GoodStream - HLS",
                                                    url = m3u8Url
                                                ).apply {
                                                    this.quality = Qualities.Unknown.value
                                                    this.referer = src
                                                }
                                            )
                                            foundLinks++
                                        }
                                    } catch (_: Exception) {}
                                } else {
                                    // Fallback to CloudStream's built-in extractors
                                    loadExtractor(src, data, subtitleCallback, callback)
                                    foundLinks++
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            // Look for video elements or scripts
            document.select("video source").forEach { source ->
                val src = source.attr("src")
                if (src.isNotBlank()) {
                     callback.invoke(
                        newExtractorLink(
                            source = SITE4_NAME,
                            name = SITE4_NAME,
                            url = src
                        ).apply {
                            this.quality = Qualities.Unknown.value
                        }
                    )
                    foundLinks++
                }
            }

            // Look for evp_play iframe (local player)
            document.select("iframe[src*='evp_play']").forEach { iframe ->
                val src = iframe.attr("src")
                val fullIframeUrl = fixUrl(src)
                
                try {
                    // 1. Fetch the player page with Referer
                    val playerDoc = app.get(fullIframeUrl, headers = mapOf("Referer" to data)).document
                    
                    // 2. Extract CONFIG data
                    val scriptContent = playerDoc.select("script").find { it.data().contains("const CONFIG =") }?.data()
                    
                    if (scriptContent != null) {
                        val ajaxUrl = Regex("ajax_url['\"]?:\\s*['\"]([^'\"]+)['\"]").find(scriptContent)?.groupValues?.get(1)?.replace("\\/", "/")
                        val encryptedId = Regex("encrypted_id['\"]?:\\s*['\"]([^'\"]+)['\"]").find(scriptContent)?.groupValues?.get(1)
                        val nonce = Regex("nonce['\"]?:\\s*['\"]([^'\"]+)['\"]").find(scriptContent)?.groupValues?.get(1)
                        
                        if (ajaxUrl != null && encryptedId != null && nonce != null) {
                            // 3. Make POST request
                            val postData = mapOf(
                                "action" to "evp_get_video_url",
                                "encrypted_id" to encryptedId,
                                "nonce" to nonce
                            )
                            
                            val jsonResponse = app.post(ajaxUrl, data = postData, headers = mapOf("Referer" to fullIframeUrl)).text
                            
                            // 4. Extract URLs from JSON
                            // Response format: {"success":true,"data":{"servers":["url1", "url2", ...]}}
                            val urlRegex = Regex(""""src"\s*:\s*"([^"]+)"""") // Based on typical JSON structure or just simple string extraction if array
                            // The test output showed: "servers":["url1","url2"]
                            // So we can extract string literals inside the servers array
                            
                            // Let's use a simpler regex that captures urls inside the servers array directly or iteratively
                            // Since we don't have a full JSON parser handy or want to keep it simple:
                            val serversBlock = Regex(""""servers"\s*:\s*\[(.*?)\]""").find(jsonResponse)?.groupValues?.get(1)
                            
                            if (serversBlock != null) {
                                val urlMatches = Regex(""""([^"]+)"""").findAll(serversBlock)
                                urlMatches.forEach { match ->
                                    val videoUrl = match.groupValues[1].replace("\\/", "/")
                                    
                                    callback.invoke(
                                        newExtractorLink(
                                            source = SITE4_NAME,
                                            name = "$SITE4_NAME - EVP",
                                            url = videoUrl
                                        ).apply {
                                            this.quality = Qualities.Unknown.value
                                            this.referer = fullIframeUrl
                                        }
                                    )
                                    foundLinks++
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            foundLinks > 0
        } catch (_: Exception) {
            false
        }
    }

    // Site 5 link extraction (PersianHive)
    private suspend fun loadLinksSite5(
        data: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val document = app.get(data).document
            var foundLinks = false
            
            // Check for iframes
            document.select("iframe").forEach { iframe ->
                val src = iframe.attr("src")
                if (src.isNotBlank()) {
                    loadExtractor(src, data, subtitleCallback, callback)
                    foundLinks = true
                }
            }

            // Check for PlayerJS source in scripts
            document.select("script").forEach { script ->
                val scriptContent = script.data()
                if (scriptContent.contains("file:")) {
                    val fileRegex = Regex("""file\s*:\s*["']([^"']+)["']""")
                    fileRegex.findAll(scriptContent).forEach { match ->
                        val videoUrl = match.groupValues[1]
                        if (videoUrl.endsWith(".m3u8") || videoUrl.endsWith(".mp4")) {
                            callback.invoke(
                                newExtractorLink(
                                    source = SITE5_NAME,
                                    name = "$SITE5_NAME - Stream",
                                    url = videoUrl
                                ).apply {
                                    this.quality = Qualities.Unknown.value
                                    this.referer = data
                                }
                            )
                            foundLinks = true
                        }
                    }
                }
            }
            foundLinks
        } catch (e: Exception) {
            false
        }
    }
}
