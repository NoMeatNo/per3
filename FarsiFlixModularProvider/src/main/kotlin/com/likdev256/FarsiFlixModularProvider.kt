package com.likdev256

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import me.xdrop.fuzzywuzzy.FuzzySearch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * FarsiFlix Modular Provider
 * 
 * A modular implementation that delegates to individual site handlers.
 * Each site (DiyGuide, FarsiPlex, FarsiLand, IranTamasha, PersianHive) 
 * has its own file with encapsulated parsing logic.
 */
class FarsiFlixModularProvider : MainAPI() {
    override var mainUrl = "https://farsiplex.com"
    override var name = "FarsiFlix Modular"
    override val hasMainPage = true
    override var lang = "fa"
    override val hasDownloadSupport = true
    override var sequentialMainPage = true
    override var sequentialMainPageDelay: Long = 100
    override val supportedTypes = setOf(
        TvType.Movie, TvType.TvSeries, TvType.Live
    )

    // All site handlers - pass 'this' (MainAPI) for extension function access
    private val siteHandlers: List<SiteHandler> by lazy {
        listOf(
            Site1DiyGuide(this),
            Site2FarsiPlex(this),
            Site3FarsiLand(this),
            Site4IranTamasha(this),
            Site5PersianHive(this),
        )
    }
    
    // Reference to Site5 for Cloudflare interceptor
    private val site5 by lazy { siteHandlers.filterIsInstance<Site5PersianHive>().first() }

    // Build main page from all site handlers
    override val mainPage by lazy {
        mainPageOf(*siteHandlers.flatMap { handler ->
            handler.mainPages.map { it.first to it.second }
        }.toTypedArray())
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        // Find the handler for this request
        val handler = siteHandlers.find { it.handles(request.data) }
        
        // Wrap in try-catch so one failing site doesn't break the whole home page
        val home = try {
            // Use Cloudflare bypass for PersianHive with timeout to prevent long blocking
            val document = if (handler is Site5PersianHive) {
                kotlinx.coroutines.withTimeoutOrNull(15000L) {
                    app.get(request.data, interceptor = site5.cfKiller).document
                } ?: return newHomePageResponse(request.name, emptyList()) // Timeout - return empty
            } else {
                app.get(request.data).document
            }
            
            if (handler != null) {
                val selector = handler.getHomeSelector(request.data)
                when (handler) {
                    is Site5PersianHive -> {
                        when {
                            request.data.contains("live-tv") -> {
                                document.select(selector).mapNotNull { handler.parseLiveItem(it) }
                            }
                            request.data.contains("movies") -> {
                                document.select(selector).mapNotNull { handler.parseMovieItem(it) }
                            }
                            else -> {
                                document.select(selector).mapNotNull { handler.parseHomeItem(it) }
                            }
                        }
                    }
                    else -> {
                        document.select(selector).mapNotNull { handler.parseHomeItem(it) }
                    }
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            // If a site fails (e.g., Cloudflare issues), return empty list instead of crashing
            emptyList()
        }
        
        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val allResults = mutableListOf<SearchResponse>()

        // Search all sites in parallel
        coroutineScope {
            val deferredResults = siteHandlers.map { handler ->
                async {
                    try {
                        handler.search(query)
                    } catch (e: Exception) { emptyList() }
                }
            }
            
            deferredResults.awaitAll().forEach { results ->
                allResults.addAll(results)
            }
        }

        // Sort by fuzzy match score
        return allResults.sortedBy { 
            -FuzzySearch.partialRatio(
                it.name.replace("(\\()+(.*)+(\\))".toRegex(), "").lowercase(), 
                query.lowercase()
            ) 
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        // Find the handler for this URL
        val handler = siteHandlers.find { it.handles(url) }
        
        // Use Cloudflare bypass for PersianHive
        val document = if (handler is Site5PersianHive) {
            app.get(url, interceptor = site5.cfKiller).document
        } else {
            app.get(url).document
        }
        
        return handler?.load(url, document)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Find the handler for this data URL
        val handler = siteHandlers.find { it.handles(data) }
        
        return handler?.loadLinks(data, subtitleCallback, callback) ?: false
    }
}
