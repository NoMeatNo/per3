package com.likdev256

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import me.xdrop.fuzzywuzzy.FuzzySearch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * FarsiFlix Super Provider
 * 
 * A unified provider that aggregates content from all Persian streaming sites
 * into a single catalog with cross-site source aggregation.
 * 
 * Architecture:
 * - Unified home page with Movies, Series, Old Movies, Old Series, Live TV
 * - Content is deduplicated across sites using fuzzy title matching
 * - When playing, sources are aggregated from ALL sites that have the content
 */
class FarsiFlixSuperProvider : MainAPI() {
    override var mainUrl = "https://farsiplex.com"
    override var name = "FarsiFlix Super"
    override val hasMainPage = true
    override var lang = "fa"
    override val hasDownloadSupport = true
    override var sequentialMainPage = true
    override var sequentialMainPageDelay: Long = 100
    override val supportedTypes = setOf(
        TvType.Movie, TvType.TvSeries, TvType.Live
    )

    companion object {
        const val FUZZY_MATCH_THRESHOLD = 85
        const val CROSS_SITE_FUZZY_THRESHOLD = 65
    }

    // All site handlers
    private val siteHandlers: List<SiteHandler> by lazy {
        listOf(
            Site1DiyGuide(this),
            Site2FarsiPlex(this),
            Site3FarsiLand(this),
            Site4IranTamasha(this),
            Site5PersianHive(this),
        )
    }
    
    // Quick access to specific handlers
    private val site1 by lazy { siteHandlers.filterIsInstance<Site1DiyGuide>().first() }
    private val site2 by lazy { siteHandlers.filterIsInstance<Site2FarsiPlex>().first() }
    private val site3 by lazy { siteHandlers.filterIsInstance<Site3FarsiLand>().first() }
    private val site4 by lazy { siteHandlers.filterIsInstance<Site4IranTamasha>().first() }
    private val site5 by lazy { siteHandlers.filterIsInstance<Site5PersianHive>().first() }

    // Unified home page sections (not per-site)
    override val mainPage = mainPageOf(
        "movies" to "Movies",
        "series" to "Series",
        "old-movies" to "Old Movies",
        "old-series" to "Old Series",
        "live-tv" to "Live TV"
    )

    /**
     * Data class for unified content with multiple sources
     */
    data class UnifiedContent(
        val title: String,
        val normalizedTitle: String,
        val posterUrl: String?,
        val type: TvType,
        val sources: MutableList<Pair<String, String>> // siteName to url
    )

    /**
     * Normalize title for fuzzy matching
     */
    private fun normalizeTitle(title: String): String {
        return title.lowercase()
            .replace(Regex("[^a-z0-9\\u0600-\\u06FF\\s]"), "") // Keep alphanumeric, Persian, and spaces
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Extract URL slug for keyword matching
     */
    private fun extractSlugKeywords(url: String): String {
        val slug = url
            .substringAfterLast("/")
            .substringBefore(".html")
            .substringBefore(".htm")
            .replace("-", " ")
            .replace("_", " ")
            .replace(Regex("\\s+(hd|kamel|full|720p|1080p|480p)\\s*$", RegexOption.IGNORE_CASE), "")
            .trim()
        return if (slug.length > 2) slug else ""
    }

    /**
     * Merge content by title similarity
     */
    private fun mergeContentByTitle(content: List<UnifiedContent>): List<UnifiedContent> {
        val merged = mutableListOf<UnifiedContent>()
        
        for (item in content) {
            val existingIndex = merged.indexOfFirst { existing ->
                FuzzySearch.ratio(existing.normalizedTitle, item.normalizedTitle) >= FUZZY_MATCH_THRESHOLD
            }
            
            if (existingIndex >= 0) {
                // Merge sources - avoid duplicates
                item.sources.forEach { source ->
                    if (merged[existingIndex].sources.none { it.first == source.first }) {
                        merged[existingIndex].sources.add(source)
                    }
                }
                // Use better poster if available
                if (merged[existingIndex].posterUrl == null && item.posterUrl != null) {
                    merged[existingIndex] = merged[existingIndex].copy(posterUrl = item.posterUrl)
                }
            } else {
                merged.add(item)
            }
        }
        
        return merged
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val allContent = mutableListOf<UnifiedContent>()
        
        coroutineScope {
            when (request.name) {
                "Movies" -> {
                    // Fetch movies from Site1, Site2, Site3, Site5 in parallel
                    val deferreds = listOf(
                        async {
                            try {
                                app.get("${site1.siteUrl}/movies.html").document
                                    .select(site1.getHomeSelector("movies"))
                                    .mapNotNull { elem ->
                                        site1.parseHomeItem(elem)?.let { sr ->
                                            UnifiedContent(sr.name, normalizeTitle(sr.name), sr.posterUrl, TvType.Movie, 
                                                mutableListOf(site1.siteName to sr.url))
                                        }
                                    }
                            } catch (_: Exception) { emptyList() }
                        },
                        async {
                            try {
                                app.get("${site2.siteUrl}/movie/").document
                                    .select(site2.getHomeSelector("movie"))
                                    .mapNotNull { elem ->
                                        site2.parseHomeItem(elem)?.let { sr ->
                                            UnifiedContent(sr.name, normalizeTitle(sr.name), sr.posterUrl, TvType.Movie,
                                                mutableListOf(site2.siteName to sr.url))
                                        }
                                    }
                            } catch (_: Exception) { emptyList() }
                        },
                        async {
                            try {
                                app.get("${site3.siteUrl}/iran-movie-2025/").document
                                    .select(site3.getHomeSelector("movie"))
                                    .mapNotNull { elem ->
                                        site3.parseHomeItem(elem)?.let { sr ->
                                            UnifiedContent(sr.name, normalizeTitle(sr.name), sr.posterUrl, TvType.Movie,
                                                mutableListOf(site3.siteName to sr.url))
                                        }
                                    }
                            } catch (_: Exception) { emptyList() }
                        },
                        async {
                            try {
                                kotlinx.coroutines.withTimeoutOrNull(15000L) {
                                    app.get("${site5.siteUrl}/movies_films/", interceptor = site5.cfKiller).document
                                        .select(site5.getHomeSelector("movies"))
                                        .mapNotNull { elem ->
                                            site5.parseMovieItem(elem)?.let { sr ->
                                                UnifiedContent(sr.name, normalizeTitle(sr.name), sr.posterUrl, TvType.Movie,
                                                    mutableListOf(site5.siteName to sr.url))
                                            }
                                        }
                                } ?: emptyList()
                            } catch (_: Exception) { emptyList() }
                        }
                    )
                    deferreds.awaitAll().forEach { allContent.addAll(it) }
                }
                
                "Series" -> {
                    // Fetch series from all 5 sites
                    val deferreds = listOf(
                        async {
                            try {
                                app.get("${site1.siteUrl}/tv-series.html").document
                                    .select(site1.getHomeSelector("series"))
                                    .mapNotNull { elem ->
                                        site1.parseHomeItem(elem)?.let { sr ->
                                            UnifiedContent(sr.name, normalizeTitle(sr.name), sr.posterUrl, TvType.TvSeries,
                                                mutableListOf(site1.siteName to sr.url))
                                        }
                                    }
                            } catch (_: Exception) { emptyList() }
                        },
                        async {
                            try {
                                app.get("${site2.siteUrl}/tvshow/").document
                                    .select(site2.getHomeSelector("tvshow"))
                                    .mapNotNull { elem ->
                                        site2.parseHomeItem(elem)?.let { sr ->
                                            UnifiedContent(sr.name, normalizeTitle(sr.name), sr.posterUrl, TvType.TvSeries,
                                                mutableListOf(site2.siteName to sr.url))
                                        }
                                    }
                            } catch (_: Exception) { emptyList() }
                        },
                        async {
                            try {
                                app.get("${site3.siteUrl}/series-26/").document
                                    .select(site3.getHomeSelector("series"))
                                    .mapNotNull { elem ->
                                        site3.parseHomeItem(elem)?.let { sr ->
                                            UnifiedContent(sr.name, normalizeTitle(sr.name), sr.posterUrl, TvType.TvSeries,
                                                mutableListOf(site3.siteName to sr.url))
                                        }
                                    }
                            } catch (_: Exception) { emptyList() }
                        },
                        async {
                            try {
                                app.get("${site4.siteUrl}/series/").document
                                    .select(site4.getHomeSelector("series"))
                                    .mapNotNull { elem ->
                                        site4.parseHomeItem(elem)?.let { sr ->
                                            UnifiedContent(sr.name, normalizeTitle(sr.name), sr.posterUrl, TvType.TvSeries,
                                                mutableListOf(site4.siteName to sr.url))
                                        }
                                    }
                            } catch (_: Exception) { emptyList() }
                        },
                        async {
                            try {
                                kotlinx.coroutines.withTimeoutOrNull(15000L) {
                                    app.get("${site5.siteUrl}/series-web/", interceptor = site5.cfKiller).document
                                        .select(site5.getHomeSelector("series"))
                                        .mapNotNull { elem ->
                                            site5.parseHomeItem(elem)?.let { sr ->
                                                UnifiedContent(sr.name, normalizeTitle(sr.name), sr.posterUrl, TvType.TvSeries,
                                                    mutableListOf(site5.siteName to sr.url))
                                            }
                                        }
                                } ?: emptyList()
                            } catch (_: Exception) { emptyList() }
                        }
                    )
                    deferreds.awaitAll().forEach { allContent.addAll(it) }
                }
                
                "Old Movies" -> {
                    // FarsiLand old movies
                    try {
                        app.get("${site3.siteUrl}/old-iranian-movies/").document
                            .select(site3.getHomeSelector("movies"))
                            .mapNotNull { elem ->
                                site3.parseHomeItem(elem)?.let { sr ->
                                    UnifiedContent(sr.name, normalizeTitle(sr.name), sr.posterUrl, TvType.Movie,
                                        mutableListOf(site3.siteName to sr.url))
                                }
                            }
                            .let { allContent.addAll(it) }
                    } catch (_: Exception) {}
                }
                
                "Old Series" -> {
                    // FarsiLand old series
                    try {
                        app.get("${site3.siteUrl}/iranian-series/").document
                            .select(site3.getHomeSelector("series"))
                            .mapNotNull { elem ->
                                site3.parseHomeItem(elem)?.let { sr ->
                                    UnifiedContent(sr.name, normalizeTitle(sr.name), sr.posterUrl, TvType.TvSeries,
                                        mutableListOf(site3.siteName to sr.url))
                                }
                            }
                            .let { allContent.addAll(it) }
                    } catch (_: Exception) {}
                }
                
                "Live TV" -> {
                    // Fetch Live TV from Site1 and Site5
                    val deferreds = listOf(
                        async {
                            try {
                                app.get("${site1.siteUrl}/live-tv.html").document
                                    .select(site1.getHomeSelector("live-tv"))
                                    .mapNotNull { elem ->
                                        site1.parseLiveItem(elem)?.let { sr ->
                                            UnifiedContent(sr.name, normalizeTitle(sr.name), sr.posterUrl, TvType.Live,
                                                mutableListOf(site1.siteName to sr.url))
                                        }
                                    }
                            } catch (_: Exception) { emptyList() }
                        },
                        async {
                            try {
                                kotlinx.coroutines.withTimeoutOrNull(15000L) {
                                    app.get("${site5.siteUrl}/live-tv/", interceptor = site5.cfKiller).document
                                        .select(site5.getHomeSelector("live-tv"))
                                        .mapNotNull { elem ->
                                            site5.parseLiveItem(elem)?.let { sr ->
                                                UnifiedContent(sr.name, normalizeTitle(sr.name), sr.posterUrl, TvType.Live,
                                                    mutableListOf(site5.siteName to sr.url))
                                            }
                                        }
                                } ?: emptyList()
                            } catch (_: Exception) { emptyList() }
                        }
                    )
                    deferreds.awaitAll().forEach { allContent.addAll(it) }
                }
            }
        }
        
        // Merge duplicates
        val mergedContent = mergeContentByTitle(allContent)
        
        // Convert to SearchResponse with encoded source data
        val home = mergedContent.map { content ->
            // Encode sources as: "siteName::url|siteName::url|TITLE::title"
            val combinedData = content.sources.joinToString("|") { "${it.first}::${it.second}" } + 
                "|TITLE::${content.title}"
            newMovieSearchResponse(content.title, combinedData, content.type) {
                this.posterUrl = content.posterUrl
            }
        }
        
        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val allContent = mutableListOf<UnifiedContent>()
        val fixedQuery = query.replace(" ", "+")

        // Search all sites in parallel
        coroutineScope {
            val deferreds = listOf(
                async {
                    try {
                        app.get("${site1.siteUrl}/search?q=$fixedQuery").document
                            .select("div.col-md-2.col-sm-3.col-xs-6")
                            .mapNotNull { elem ->
                                site1.parseHomeItem(elem)?.let { sr ->
                                    UnifiedContent(sr.name, normalizeTitle(sr.name), sr.posterUrl, 
                                        if (sr.type == TvType.Live) TvType.Live else TvType.Movie,
                                        mutableListOf(site1.siteName to sr.url))
                                }
                            }
                    } catch (_: Exception) { emptyList() }
                },
                async {
                    try {
                        app.get("${site2.siteUrl}/?s=$fixedQuery").document
                            .select("div.result-item")
                            .mapNotNull { elem ->
                                (site2 as Site2FarsiPlex).parseSearchItem(elem)?.let { sr ->
                                    UnifiedContent(sr.name, normalizeTitle(sr.name), sr.posterUrl, sr.type ?: TvType.Movie,
                                        mutableListOf(site2.siteName to sr.url))
                                }
                            }
                    } catch (_: Exception) { emptyList() }
                },
                async {
                    try {
                        app.get("${site3.siteUrl}/?s=$fixedQuery").document
                            .select("div.result-item")
                            .mapNotNull { elem ->
                                (site3 as Site3FarsiLand).parseSearchItem(elem)?.let { sr ->
                                    UnifiedContent(sr.name, normalizeTitle(sr.name), sr.posterUrl, sr.type ?: TvType.Movie,
                                        mutableListOf(site3.siteName to sr.url))
                                }
                            }
                    } catch (_: Exception) { emptyList() }
                },
                async {
                    try {
                        app.get("${site4.siteUrl}/?s=$fixedQuery").document
                            .select("article.post-item")
                            .mapNotNull { elem ->
                                site4.parseHomeItem(elem)?.let { sr ->
                                    UnifiedContent(sr.name, normalizeTitle(sr.name), sr.posterUrl, TvType.TvSeries,
                                        mutableListOf(site4.siteName to sr.url))
                                }
                            }
                    } catch (_: Exception) { emptyList() }
                },
                async {
                    try {
                        kotlinx.coroutines.withTimeoutOrNull(15000L) {
                            app.get("${site5.siteUrl}/?s=$fixedQuery", interceptor = site5.cfKiller).document
                                .select("div.pciwgas-post-cat-inner")
                                .mapNotNull { elem ->
                                    site5.parseHomeItem(elem)?.let { sr ->
                                        UnifiedContent(sr.name, normalizeTitle(sr.name), sr.posterUrl, sr.type ?: TvType.TvSeries,
                                            mutableListOf(site5.siteName to sr.url))
                                    }
                                }
                        } ?: emptyList()
                    } catch (_: Exception) { emptyList() }
                }
            )
            deferreds.awaitAll().forEach { allContent.addAll(it) }
        }

        // Merge duplicates
        val mergedContent = mergeContentByTitle(allContent)
        
        return mergedContent.map { content ->
            val combinedData = content.sources.joinToString("|") { "${it.first}::${it.second}" } +
                "|TITLE::${content.title}"
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
        // Parse encoded data: "siteName::url|siteName::url|TITLE::title"
        val parts = url.split("|")
        var title: String? = null
        val sources = mutableListOf<Pair<String, String>>()
        
        for (part in parts) {
            val split = part.split("::", limit = 2)
            if (split.size == 2) {
                when (split[0]) {
                    "TITLE" -> title = split[1]
                    else -> sources.add(split[0] to split[1])
                }
            }
        }
        
        if (sources.isEmpty()) return null
        
        // Use the first source to load details
        val (siteName, sourceUrl) = sources.first()
        val handler = siteHandlers.find { it.siteName == siteName } ?: return null
        
        val document = if (handler is Site5PersianHive) {
            app.get(sourceUrl, interceptor = site5.cfKiller).document
        } else {
            app.get(sourceUrl).document
        }
        
        val loadResponse = handler.load(sourceUrl, document) ?: return null
        
        // Re-encode data with all sources + title + slug for cross-site search
        val slug = extractSlugKeywords(sourceUrl)
        val enhancedData = sources.joinToString("|") { "${it.first}::${it.second}" } +
            "|TITLE::${title ?: loadResponse.name}" +
            "|SLUG::$slug"
        
        // Modify the load response to use our enhanced data
        return when (loadResponse) {
            is MovieLoadResponse -> {
                newMovieLoadResponse(loadResponse.name, url, loadResponse.type, enhancedData) {
                    this.posterUrl = loadResponse.posterUrl
                    this.plot = loadResponse.plot
                }
            }
            is TvSeriesLoadResponse -> {
                // For series, we need to enhance each episode's data
                val enhancedEpisodes = loadResponse.episodes.map { ep ->
                    val epSlug = extractSlugKeywords(ep.data)
                    val epData = "$siteName::${ep.data}|TITLE::${title ?: loadResponse.name}|SLUG::$epSlug|SEASON::${ep.season}|EPISODE::${ep.episode}"
                    newEpisode(epData) {
                        this.name = ep.name
                        this.season = ep.season
                        this.episode = ep.episode
                        this.posterUrl = ep.posterUrl
                    }
                }
                
                newTvSeriesLoadResponse(loadResponse.name, url, loadResponse.type, enhancedEpisodes) {
                    this.posterUrl = loadResponse.posterUrl
                    this.plot = loadResponse.plot
                }
            }
            else -> loadResponse
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var foundLinks = false
        
        // Parse the encoded data
        val parts = data.split("|")
        var searchTitle: String? = null
        var urlSlug: String? = null
        var season: Int? = null
        var episode: Int? = null
        val directSources = mutableListOf<Pair<String, String>>()
        
        for (part in parts) {
            val split = part.split("::", limit = 2)
            if (split.size == 2) {
                when (split[0]) {
                    "TITLE" -> searchTitle = split[1]
                    "SLUG" -> urlSlug = split[1]
                    "SEASON" -> season = split[1].toIntOrNull()
                    "EPISODE" -> episode = split[1].toIntOrNull()
                    else -> directSources.add(split[0] to split[1])
                }
            }
        }
        
        // Load from direct sources first (in parallel)
        coroutineScope {
            val deferreds = directSources.map { (siteName, sourceUrl) ->
                async {
                    try {
                        val handler = siteHandlers.find { it.siteName == siteName }
                        handler?.loadLinks(sourceUrl, subtitleCallback, callback) ?: false
                    } catch (_: Exception) { false }
                }
            }
            if (deferreds.awaitAll().any { it }) foundLinks = true
        }
        
        // Cross-site search for additional sources
        val sitesSearched = directSources.map { it.first }.toSet()
        val searchTerm = searchTitle ?: urlSlug ?: return foundLinks
        val isEpisode = season != null && episode != null
        
        if (searchTerm.length > 2) {
            coroutineScope {
                val crossSiteDeferreds = mutableListOf<kotlinx.coroutines.Deferred<Boolean>>()
                
                // Search sites we haven't loaded from yet
                siteHandlers.filter { it.siteName !in sitesSearched }.forEach { handler ->
                    crossSiteDeferreds.add(async {
                        try {
                            val results = handler.search(searchTerm)
                            val match = results.firstOrNull { sr ->
                                FuzzySearch.ratio(normalizeTitle(sr.name), normalizeTitle(searchTerm)) >= CROSS_SITE_FUZZY_THRESHOLD
                            }
                            
                            if (match != null) {
                                if (isEpisode) {
                                    // For episodes, find the matching episode on this site
                                    val episodeUrl = findMatchingEpisode(handler, match.url, season!!, episode!!)
                                    if (episodeUrl != null) {
                                        handler.loadLinks(episodeUrl, subtitleCallback, callback)
                                    } else false
                                } else {
                                    handler.loadLinks(match.url, subtitleCallback, callback)
                                }
                            } else false
                        } catch (_: Exception) { false }
                    })
                }
                
                if (crossSiteDeferreds.awaitAll().any { it }) foundLinks = true
            }
        }
        
        return foundLinks
    }
    
    /**
     * Find a matching episode on a different site
     */
    private suspend fun findMatchingEpisode(
        handler: SiteHandler,
        showUrl: String,
        targetSeason: Int,
        targetEpisode: Int
    ): String? {
        return try {
            val document = if (handler is Site5PersianHive) {
                app.get(showUrl, interceptor = site5.cfKiller).document
            } else {
                app.get(showUrl).document
            }
            
            val loadResponse = handler.load(showUrl, document)
            
            if (loadResponse is TvSeriesLoadResponse) {
                loadResponse.episodes.find { ep ->
                    ep.season == targetSeason && ep.episode == targetEpisode
                }?.data
            } else null
        } catch (_: Exception) { null }
    }
}
