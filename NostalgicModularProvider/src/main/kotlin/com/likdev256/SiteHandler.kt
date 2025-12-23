package com.likdev256

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Base interface for all site handlers in NostalgicModularProvider.
 * Each site (NostalgikTV, FarsiLandOld) implements this interface 
 * to encapsulate its parsing logic.
 * 
 * The api parameter provides access to MainAPI extension functions.
 */
interface SiteHandler {
    val siteUrl: String
    val siteName: String
    
    /** Reference to the MainAPI for extension function access */
    val api: MainAPI
    
    /** Main page sections for this site (url to display name) */
    val mainPages: List<Pair<String, String>>
    
    /** Check if a URL belongs to this site */
    fun handles(url: String): Boolean = url.contains(siteUrl)
    
    /** Parse a home page element to SearchResponse */
    fun parseHomeItem(element: Element): SearchResponse?
    
    /** Get the CSS selector for home page items */
    fun getHomeSelector(url: String): String
    
    /** Search this site for content */
    suspend fun search(query: String): List<SearchResponse>
    
    /** Load content details from a URL */
    suspend fun load(url: String, document: Document): LoadResponse?
    
    /** Extract video links from content */
    suspend fun loadLinks(
        data: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean
}
