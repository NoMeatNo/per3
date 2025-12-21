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

class FarsiFlixUnifiedProvider : MainAPI() {
    override var mainUrl = "https://farsiplex.com"
    override var name = "FarsiFlix Unified"
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
        
        const val FUZZY_MATCH_THRESHOLD = 85
    }

    // Data class to store content with source info
    data class UnifiedContent(
        val title: String,
        val normalizedTitle: String,
        val posterUrl: String?,
        val type: TvType,
        val sourceUrls: MutableList<Pair<String, String>> // Pair<siteName, url>
    )

    override val mainPage = mainPageOf(
        "movies" to "Movies",
        "series" to "Series",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val allContent = mutableListOf<UnifiedContent>()
        
        coroutineScope {
            when (request.name) {
                "Movies" -> {
                    // Fetch movies from all sites in parallel
                    val site1Deferred = async {
                        try {
                            app.get("$SITE1_URL/movies.html").document
                                .select("div.col-md-2.col-sm-3.col-xs-6")
                                .mapNotNull { it.toSite1Content(TvType.Movie) }
                        } catch (e: Exception) { emptyList() }
                    }
                    
                    val site2Deferred = async {
                        try {
                            app.get("$SITE2_URL/movie/").document
                                .select("article.item")
                                .mapNotNull { it.toSite23Content(TvType.Movie) }
                        } catch (e: Exception) { emptyList() }
                    }
                    
                    val site3Deferred = async {
                        try {
                            app.get("$SITE3_URL/iran-movie-2025/").document
                                .select("article.item")
                                .mapNotNull { it.toSite23Content(TvType.Movie) }
                        } catch (e: Exception) { emptyList() }
                    }
                    
                    allContent.addAll(site1Deferred.await())
                    allContent.addAll(site2Deferred.await())
                    allContent.addAll(site3Deferred.await())
                }
                "Series" -> {
                    // Fetch series from all sites in parallel
                    val site1Deferred = async {
                        try {
                            app.get("$SITE1_URL/tv-series.html").document
                                .select("div.col-md-2.col-sm-3.col-xs-6")
                                .mapNotNull { it.toSite1Content(TvType.TvSeries) }
                        } catch (e: Exception) { emptyList() }
                    }
                    
                    val site2Deferred = async {
                        try {
                            app.get("$SITE2_URL/tvshow/").document
                                .select("article.item")
                                .mapNotNull { it.toSite23Content(TvType.TvSeries) }
                        } catch (e: Exception) { emptyList() }
                    }
                    
                    val site3Deferred = async {
                        try {
                            app.get("$SITE3_URL/series-26/").document
                                .select("article.item")
                                .mapNotNull { it.toSite23Content(TvType.TvSeries) }
                        } catch (e: Exception) { emptyList() }
                    }
                    
                    allContent.addAll(site1Deferred.await())
                    allContent.addAll(site2Deferred.await())
                    allContent.addAll(site3Deferred.await())
                }
            }
        }
        
        // Merge duplicates by title similarity
        val mergedContent = mergeContentByTitle(allContent)
        
        // Convert to SearchResponse
        val home = mergedContent.map { content ->
            // Encode all source URLs as comma-separated in the data
            val combinedData = content.sourceUrls.joinToString("|") { "${it.first}::${it.second}" }
            newMovieSearchResponse(content.title, combinedData, content.type) {
                this.posterUrl = content.posterUrl
            }
        }
        
        return newHomePageResponse(request.name, home)
    }

    private fun normalizeTitle(title: String): String {
        return title.lowercase()
            .replace(Regex("[^a-z0-9\\u0600-\\u06FF]"), "") // Keep alphanumeric and Persian chars
            .trim()
    }

    private fun mergeContentByTitle(content: List<UnifiedContent>): List<UnifiedContent> {
        val merged = mutableListOf<UnifiedContent>()
        
        for (item in content) {
            val existingIndex = merged.indexOfFirst { existing ->
                FuzzySearch.ratio(existing.normalizedTitle, item.normalizedTitle) >= FUZZY_MATCH_THRESHOLD
            }
            
            if (existingIndex >= 0) {
                // Merge source URLs into existing entry
                merged[existingIndex].sourceUrls.addAll(item.sourceUrls)
            } else {
                // Add as new entry
                merged.add(item)
            }
        }
        
        return merged
    }

    private fun Element.toSite1Content(type: TvType): UnifiedContent? {
        val title = this.selectFirst("div.movie-title h3 a")?.text()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("div.movie-title h3 a")?.attr("href").toString())
        val posterUrl = fixUrlNull(this.selectFirst("div.latest-movie-img-container")?.attr("data-src")?.trim())
        
        return UnifiedContent(
            title = title,
            normalizedTitle = normalizeTitle(title),
            posterUrl = posterUrl,
            type = type,
            sourceUrls = mutableListOf(SITE1_NAME to href)
        )
    }

    private fun Element.toSite23Content(type: TvType): UnifiedContent? {
        val title = this.selectFirst("div.data h3 a")?.text()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("div.data h3 a")?.attr("href").toString())
        val posterUrl = fixUrlNull(this.selectFirst("div.poster img")?.attr("src")?.trim())
        val siteName = if (href.contains("farsiland")) SITE3_NAME else SITE2_NAME
        
        return UnifiedContent(
            title = title,
            normalizedTitle = normalizeTitle(title),
            posterUrl = posterUrl,
            type = type,
            sourceUrls = mutableListOf(siteName to href)
        )
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
        val allContent = mutableListOf<UnifiedContent>()

        // Search all 3 sites in parallel
        coroutineScope {
            val site1Deferred = async {
                try {
                    app.get("$SITE1_URL/search?q=$fixedQuery")
                        .document.select("div.col-md-2.col-sm-3.col-xs-6")
                        .mapNotNull { it.toSite1Content(TvType.Movie) }
                } catch (e: Exception) { emptyList() }
            }
            
            val site2Deferred = async {
                try {
                    app.get("$SITE2_URL/?s=$fixedQuery")
                        .document.select("div.result-item")
                        .mapNotNull { elem ->
                            val titleElement = elem.selectFirst("div.details div.title a")
                            val title = titleElement?.text()?.trim() ?: return@mapNotNull null
                            val href = fixUrl(titleElement.attr("href"))
                            val posterUrl = fixUrlNull(elem.selectFirst("div.image img")?.attr("src"))
                            val type = if (elem.hasClass("tvshows")) TvType.TvSeries else TvType.Movie
                            UnifiedContent(title, normalizeTitle(title), posterUrl, type, mutableListOf(SITE2_NAME to href))
                        }
                } catch (e: Exception) { emptyList() }
            }
            
            val site3Deferred = async {
                try {
                    app.get("$SITE3_URL/?s=$fixedQuery")
                        .document.select("div.result-item")
                        .mapNotNull { elem ->
                            val titleElement = elem.selectFirst("div.details div.title a")
                            val title = titleElement?.text()?.trim() ?: return@mapNotNull null
                            val href = fixUrl(titleElement.attr("href"))
                            val posterUrl = fixUrlNull(elem.selectFirst("div.image img")?.attr("src"))
                            val type = if (elem.hasClass("tvshows")) TvType.TvSeries else TvType.Movie
                            UnifiedContent(title, normalizeTitle(title), posterUrl, type, mutableListOf(SITE3_NAME to href))
                        }
                } catch (e: Exception) { emptyList() }
            }

            allContent.addAll(site1Deferred.await())
            allContent.addAll(site2Deferred.await())
            allContent.addAll(site3Deferred.await())
        }

        // Merge duplicates
        val mergedContent = mergeContentByTitle(allContent)
        
        return mergedContent.map { content ->
            val combinedData = content.sourceUrls.joinToString("|") { "${it.first}::${it.second}" }
            newMovieSearchResponse(content.title, combinedData, content.type) {
                this.posterUrl = content.posterUrl
            }
        }.sortedBy { 
            -FuzzySearch.partialRatio(
                it.name.replace("(\\()+(.*)+(\\))".toRegex(), "").lowercase(), 
                query.lowercase()
            ) 
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        // Parse the combined data format: "siteName::url|siteName::url|..."
        val sources = url.split("|").mapNotNull { part ->
            val split = part.split("::")
            if (split.size == 2) split[0] to split[1] else null
        }
        
        if (sources.isEmpty()) return null
        
        // Use the first source to load details
        val (siteName, sourceUrl) = sources.first()
        val document = app.get(sourceUrl).document
        
        // Determine which loader to use based on the URL
        val loadResponse = when {
            sourceUrl.contains(SITE1_URL) -> loadSite1(sourceUrl, document, url)
            sourceUrl.contains(SITE2_URL) -> loadSite2(sourceUrl, document, url)
            sourceUrl.contains(SITE3_URL) -> loadSite3(sourceUrl, document, url)
            else -> null
        }
        
        return loadResponse
    }

    private suspend fun loadSite1(sourceUrl: String, document: Document, combinedData: String): LoadResponse? {
        val title = document.selectFirst("div.col-sm-9 p.m-t-10 strong")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("video#play")?.attr("poster"))
        val isTvSeries = document.select(".col-md-12.col-sm-12:has(div.owl-carousel)").isNotEmpty()

        return if (isTvSeries) {
            val episodes = mutableListOf<Episode>()
            val rows = document.select(".row")
            var seasonNumber = 0
            
            rows.forEach { row ->
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
                            // Store combined data for episode
                            episodes.add(newEpisode("$SITE1_NAME::$episodeLink|SEARCH::$title") {
                                name = episodeName
                                season = seasonNumber
                                episode = episodeNumber
                            })
                        }
                    }
                }
            }

            newTvSeriesLoadResponse(title, combinedData, TvType.TvSeries, episodes) {
                this.posterUrl = poster
            }
        } else {
            newMovieLoadResponse(title, combinedData, TvType.Movie, combinedData) {
                this.posterUrl = poster
            }
        }
    }

    private suspend fun loadSite2(sourceUrl: String, document: Document, combinedData: String): LoadResponse? {
        val isTvSeries = sourceUrl.contains("/tvshow/")
        val isMovie = sourceUrl.contains("/movie/")
        val isEpisode = sourceUrl.contains("/episode/")

        val title = when {
            isTvSeries -> document.selectFirst("div.data h1")?.text()?.trim()
            isMovie -> document.selectFirst("div.data h1")?.text()?.trim()
            isEpisode -> document.selectFirst("div#info h2")?.text()?.trim()
            else -> document.selectFirst("div.data h2")?.text()?.trim()
        } ?: return null

        val poster = when {
            isTvSeries -> fixUrlNull(document.selectFirst("div.poster img")?.attr("src"))
            isMovie -> fixUrlNull(document.selectFirst("div.playbox img.cover")?.attr("src"))
            isEpisode -> fixUrlNull(document.selectFirst("#fakeplayer .playbox img.cover")?.attr("src"))
            else -> fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
        }
        
        val plot = document.selectFirst("div.contenido p")?.text()?.trim()
            ?: document.selectFirst("div#info div.wp-content p")?.text()?.trim()

        return if (isTvSeries) {
            val episodes = mutableListOf<Episode>()

            document.select("#seasons .se-c").forEach { seasonElement ->
                val seasonNumber = seasonElement.selectFirst(".se-t")?.text()?.toIntOrNull() ?: return@forEach
                seasonElement.select("ul.episodios li").forEach { episodeElement ->
                    val epNumber = episodeElement.selectFirst(".numerando")?.text()
                        ?.substringAfter("-")?.trim()?.toIntOrNull() ?: return@forEach
                    val epTitle = episodeElement.selectFirst(".episodiotitle a")?.text() ?: return@forEach
                    val epLink = fixUrl(episodeElement.selectFirst(".episodiotitle a")?.attr("href") ?: return@forEach)

                    episodes.add(newEpisode("$SITE2_NAME::$epLink|SEARCH::$title") {
                        name = epTitle
                        season = seasonNumber
                        episode = epNumber
                    })
                }
            }

            newTvSeriesLoadResponse(title, combinedData, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
            }
        } else {
            newMovieLoadResponse(title, combinedData, TvType.Movie, combinedData) {
                this.posterUrl = poster
                this.plot = plot
            }
        }
    }

    private suspend fun loadSite3(sourceUrl: String, document: Document, combinedData: String): LoadResponse? {
        val isTvSeries = sourceUrl.contains("/tvshows/")
        val isEpisode = sourceUrl.contains("/episodes/")

        val title = when {
            isTvSeries -> document.selectFirst("div.data h1")?.text()?.trim()
            isEpisode -> document.selectFirst("div#info h2")?.text()?.trim()
            else -> document.selectFirst("div.data h2")?.text()?.trim()
        } ?: return null

        val poster = when {
            isTvSeries -> fixUrlNull(document.selectFirst("div.poster img")?.attr("src"))
            isEpisode -> fixUrlNull(document.selectFirst("#fakeplayer .playbox img.cover")?.attr("src"))
            else -> fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
        }
        
        val plot = document.selectFirst("div.contenido p")?.text()?.trim()
            ?: document.selectFirst("div#info div.wp-content p")?.text()?.trim()

        return if (isTvSeries) {
            val episodes = mutableListOf<Episode>()

            document.select("#seasons .se-c").forEach { seasonElement ->
                val seasonNumber = seasonElement.selectFirst(".se-t")?.text()?.toIntOrNull() ?: return@forEach
                seasonElement.select("ul.episodios li").forEach { episodeElement ->
                    val epNumber = episodeElement.selectFirst(".numerando")?.text()
                        ?.substringAfter("-")?.trim()?.toIntOrNull() ?: return@forEach
                    val epTitle = episodeElement.selectFirst(".episodiotitle a")?.text() ?: return@forEach
                    val epLink = fixUrl(episodeElement.selectFirst(".episodiotitle a")?.attr("href") ?: return@forEach)

                    episodes.add(newEpisode("$SITE3_NAME::$epLink|SEARCH::$title") {
                        name = epTitle
                        season = seasonNumber
                        episode = epNumber
                    })
                }
            }

            newTvSeriesLoadResponse(title, combinedData, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
            }
        } else {
            newMovieLoadResponse(title, combinedData, TvType.Movie, combinedData) {
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
        var foundLinks = false
        
        // Parse the combined data format
        val parts = data.split("|")
        var searchTitle: String? = null
        val directSources = mutableListOf<Pair<String, String>>()
        
        for (part in parts) {
            val split = part.split("::")
            if (split.size == 2) {
                if (split[0] == "SEARCH") {
                    searchTitle = split[1]
                } else {
                    directSources.add(split[0] to split[1])
                }
            }
        }
        
        // Load from direct sources
        for ((siteName, sourceUrl) in directSources) {
            when (siteName) {
                SITE1_NAME -> if (loadLinksSite1(sourceUrl, subtitleCallback, callback)) foundLinks = true
                SITE2_NAME -> if (loadLinksSite2(sourceUrl, subtitleCallback, callback)) foundLinks = true
                SITE3_NAME -> if (loadLinksSite3(sourceUrl, subtitleCallback, callback)) foundLinks = true
            }
        }
        
        // If we have a search title, search other sites for the same content
        if (searchTitle != null && searchTitle.length > 2) {
            val sitesSearched = directSources.map { it.first }.toSet()
            
            coroutineScope {
                if (SITE1_NAME !in sitesSearched) {
                    async {
                        try {
                            val results = app.get("$SITE1_URL/search?q=${searchTitle.replace(" ", "+")}")
                                .document.select("div.col-md-2.col-sm-3.col-xs-6")
                            
                            results.firstOrNull { elem ->
                                val title = elem.selectFirst("div.movie-title h3 a")?.text()?.trim() ?: ""
                                FuzzySearch.ratio(normalizeTitle(title), normalizeTitle(searchTitle)) >= FUZZY_MATCH_THRESHOLD
                            }?.let { elem ->
                                val url = fixUrl(elem.selectFirst("div.movie-title h3 a")?.attr("href") ?: "")
                                if (url.isNotBlank()) loadLinksSite1(url, subtitleCallback, callback)
                            }
                        } catch (_: Exception) {}
                    }
                }
                
                if (SITE2_NAME !in sitesSearched) {
                    async {
                        try {
                            val results = app.get("$SITE2_URL/?s=${searchTitle.replace(" ", "+")}")
                                .document.select("div.result-item")
                            
                            results.firstOrNull { elem ->
                                val title = elem.selectFirst("div.details div.title a")?.text()?.trim() ?: ""
                                FuzzySearch.ratio(normalizeTitle(title), normalizeTitle(searchTitle)) >= FUZZY_MATCH_THRESHOLD
                            }?.let { elem ->
                                val url = fixUrl(elem.selectFirst("div.details div.title a")?.attr("href") ?: "")
                                if (url.isNotBlank()) loadLinksSite2(url, subtitleCallback, callback)
                            }
                        } catch (_: Exception) {}
                    }
                }
                
                if (SITE3_NAME !in sitesSearched) {
                    async {
                        try {
                            val results = app.get("$SITE3_URL/?s=${searchTitle.replace(" ", "+")}")
                                .document.select("div.result-item")
                            
                            results.firstOrNull { elem ->
                                val title = elem.selectFirst("div.details div.title a")?.text()?.trim() ?: ""
                                FuzzySearch.ratio(normalizeTitle(title), normalizeTitle(searchTitle)) >= FUZZY_MATCH_THRESHOLD
                            }?.let { elem ->
                                val url = fixUrl(elem.selectFirst("div.details div.title a")?.attr("href") ?: "")
                                if (url.isNotBlank()) loadLinksSite3(url, subtitleCallback, callback)
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        }

        return foundLinks
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
                                    source = SITE1_NAME,
                                    name = SITE1_NAME,
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
                    if (iframeSrc.isNotBlank() && (iframeSrc.contains("ok.ru") || iframeSrc.contains("vk.com"))) {
                        loadExtractor(iframeSrc, SITE1_URL, subtitleCallback, callback)
                        foundLinks = true
                    }
                }
                
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
            
            val watchForm = document.selectFirst("form[id^=watch-]")
            val playerDocument = if (watchForm != null) {
                val formAction = watchForm.attr("action")
                val formId = watchForm.selectFirst("input[name=id]")?.attr("value") ?: ""
                
                if (formAction.isNotEmpty() && formId.isNotEmpty()) {
                    app.post(formAction, data = mapOf("id" to formId), referer = data).document
                } else document
            } else document
            
            playerDocument.select("iframe.metaframe, iframe.rptss, iframe[src*='source=']").forEach { iframe ->
                val iframeSrc = iframe.attr("src")
                if (iframeSrc.contains("source=")) {
                    val sourceParam = iframeSrc.substringAfter("source=").substringBefore("&")
                    val decodedUrl = java.net.URLDecoder.decode(sourceParam, "UTF-8")
                    
                    if (decodedUrl.isNotBlank() && (decodedUrl.endsWith(".mp4") || decodedUrl.endsWith(".m3u8"))) {
                        val quality = when {
                            decodedUrl.contains("1080") -> Qualities.P1080.value
                            decodedUrl.contains("720") -> Qualities.P720.value
                            decodedUrl.contains("480") -> Qualities.P480.value
                            decodedUrl.contains("360") -> Qualities.P360.value
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
                                url = decodedUrl
                            ).apply {
                                this.quality = quality
                                this.referer = SITE2_URL
                            }
                        )
                        foundLinks++
                    }
                }
            }
            
            if (foundLinks == 0) {
                val postId = playerDocument.selectFirst("li.dooplay_player_option[data-post]")?.attr("data-post")
                    ?: playerDocument.selectFirst("[data-post]")?.attr("data-post")
                
                if (postId != null) {
                    val playerOptions = playerDocument.select("li.dooplay_player_option")
                    playerOptions.forEach { option ->
                        val nume = option.attr("data-nume")
                        val dataType = option.attr("data-type")
                        val qualityLabel = option.selectFirst("span.title")?.text() ?: "Unknown"
                        
                        try {
                            val apiUrl = "$SITE2_URL/wp-json/dooplayer/v2/$postId/$dataType/$nume"
                            val apiResponse = app.get(apiUrl).text
                            
                            val embedUrlRegex = Regex(""""embed_url"\s*:\s*"([^"]+)"""")
                            val embedMatch = embedUrlRegex.find(apiResponse)
                            if (embedMatch != null) {
                                val embedUrl = embedMatch.groupValues[1].replace("\\/", "/")
                                
                                if (embedUrl.contains("source=")) {
                                    val sourceParam = embedUrl.substringAfter("source=").substringBefore("&")
                                    val decodedUrl = java.net.URLDecoder.decode(sourceParam, "UTF-8")
                                    
                                    if (decodedUrl.isNotBlank() && (decodedUrl.endsWith(".mp4") || decodedUrl.endsWith(".m3u8"))) {
                                        val quality = when {
                                            qualityLabel.contains("1080") || decodedUrl.contains("1080") -> Qualities.P1080.value
                                            qualityLabel.contains("720") || decodedUrl.contains("720") -> Qualities.P720.value
                                            qualityLabel.contains("480") || decodedUrl.contains("480") -> Qualities.P480.value
                                            qualityLabel.contains("360") || decodedUrl.contains("360") -> Qualities.P360.value
                                            else -> Qualities.Unknown.value
                                        }
                                        
                                        callback.invoke(
                                            newExtractorLink(
                                                source = SITE2_NAME,
                                                name = "$SITE2_NAME ${qualityLabel}p",
                                                url = decodedUrl
                                            ).apply {
                                                this.quality = quality
                                                this.referer = SITE2_URL
                                            }
                                        )
                                        foundLinks++
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
            
            if (foundLinks == 0) {
                val scriptContent = playerDocument.selectFirst("script:containsData('video/mp4')")?.data() ?: ""
                val mp4LinkRegex = Regex("""src:\s*['"]?(https?://[^'"]+\.mp4)['"]?""")
                mp4LinkRegex.findAll(scriptContent).forEach { matchResult ->
                    val mp4Link = matchResult.groupValues[1]
                    if (mp4Link.isNotBlank()) {
                        val quality = when {
                            mp4Link.contains("1080") -> Qualities.P1080.value
                            mp4Link.contains("720") -> Qualities.P720.value
                            mp4Link.contains("480") -> Qualities.P480.value
                            else -> Qualities.Unknown.value
                        }
                        callback.invoke(
                            newExtractorLink(
                                source = SITE2_NAME,
                                name = SITE2_NAME,
                                url = mp4Link
                            ).apply {
                                this.quality = quality
                                this.referer = SITE2_URL
                            }
                        )
                        foundLinks++
                    }
                }
            }
            
            foundLinks > 0
        } catch (_: Exception) { false }
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
            } else false
        } catch (_: Exception) { false }
    }

    private fun extractMp4LinkSite3(page: Document): String {
        val mp4Link = page.select("video.jw-video").attr("src")
        if (mp4Link.isNotBlank()) return mp4Link
        
        page.select("script").forEach { scriptElement ->
            val scriptContent = scriptElement.html()
            if (scriptContent.contains("sources: [")) {
                val mp4Pattern = """file:\s*['"]([^'"]+)['"]""".toRegex()
                val matchResult = mp4Pattern.find(scriptContent)
                if (matchResult != null) return matchResult.groupValues[1]
            }
        }
        return ""
    }
}
