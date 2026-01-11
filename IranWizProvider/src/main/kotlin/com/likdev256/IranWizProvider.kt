package com.likdev256

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson

/**
 * IranWiz Provider - Persian Live TV from GLWiz
 * 
 * Stream extraction pattern:
 * 1. Initialize session by loading Player.aspx (gets ASP.NET_SessionId cookie)
 * 2. Fetch channel list from localStorage data via initial page load
 * 3. Call Ajax.aspx?action=getStreamURL&itemName=CHANNEL
 * 4. Response contains HLS m3u8 URL with auth tokens
 */
class IranWizProvider : MainAPI() {
    override var mainUrl = "https://www.glwiz.com"
    override var name = "IranWiz"
    override val hasMainPage = true
    override var lang = "fa"
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Live)
    
    private val playerBaseUrl = "$mainUrl/Pages/Player"
    private val ajaxUrl = "$playerBaseUrl/Ajax.aspx"
    
    // Logo base URL discovered from localStorage
    private val logoBaseUrl = "https://hd200.glwiz.com/menu/epg/imagesNew/cim_"
    
    // Categories matching GLWiz genre IDs
    private val categoryMap = mapOf(
        1 to "📺 Persian",
        89 to "📰 News",
        19 to "🎬 Movies & Series",
        2 to "📡 IRIB (Erasaneh)",
        10 to "🎵 Music",
        84 to "⚽ Sports",
        4 to "👶 Kids",
        85 to "🕌 Religious",
        3 to "🌍 Other Languages"
    )
    
    // Cache for channel list
    private var channelCache: List<GLWizChannel>? = null
    
    // Data class for channel info from GLWiz
    data class GLWizChannel(
        val id: Int,
        val name: String,           // Internal name for stream URL
        val un: String,             // Display name (often in Farsi)
        val genreID: Int,
        val VisibleNumber: String?  // Channel number
    ) {
        val displayName: String get() = un.ifEmpty { name }
        val logoUrl: String get() = "https://hd200.glwiz.com/menu/epg/imagesNew/cim_$id.png"
        val streamName: String get() = name.replace(" ", "")
    }
    
    // Response wrapper for GLWiz API
    data class GLWizResponse(
        val resp: GLWizRespData?
    )
    
    data class GLWizRespData(
        val Table: List<GLWizChannel>?
    )
    
    // Main page categories
    override val mainPage = mainPageOf(
        "1" to "📺 Persian",
        "89" to "📰 News",
        "19" to "🎬 Movies & Series",
        "2" to "📡 IRIB",
        "10" to "🎵 Music",
        "84" to "⚽ Sports",
        "4" to "👶 Kids",
        "85" to "🕌 Religious",
        "3" to "🌍 Other Languages"
    )
    
    /**
     * Fetch and parse channel list from GLWiz
     */
    private suspend fun fetchChannels(): List<GLWizChannel> {
        // Return cached data if available
        channelCache?.let { return it }
        
        try {
            // Load the player page which contains channel data in a script
            val playerPage = app.get(
                "$playerBaseUrl/Player.aspx",
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                )
            ).text
            
            // Try to extract channel data from LiveData or API call
            // The data is loaded via AJAX and stored in localStorage
            // We'll fetch it directly from the API
            val liveDataUrl = "$ajaxUrl?action=getLiveData&screenWidth=1920&screenHeight=1080"
            val liveResponse = app.get(
                liveDataUrl,
                headers = mapOf(
                    "Referer" to "$playerBaseUrl/Player.aspx",
                    "X-Requested-With" to "XMLHttpRequest"
                )
            ).text
            
            // Parse the JSON response
            try {
                val response = parseJson<GLWizResponse>(liveResponse)
                val channels = response.resp?.Table ?: emptyList()
                if (channels.isNotEmpty()) {
                    channelCache = channels
                    return channels
                }
            } catch (e: Exception) {
                // JSON parsing failed, try regex extraction
            }
            
            // Fallback: extract from page script
            val tableRegex = Regex(""""Table"\s*:\s*\[([^\]]+)\]""")
            val match = tableRegex.find(liveResponse) ?: tableRegex.find(playerPage)
            if (match != null) {
                try {
                    val channels = parseJson<List<GLWizChannel>>("[${match.groupValues[1]}]")
                    channelCache = channels
                    return channels
                } catch (e: Exception) {
                    // Fallback parsing failed
                }
            }
        } catch (e: Exception) {
            // Network error
        }
        
        // Return empty list if all else fails
        return emptyList()
    }
    
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val channels = fetchChannels()
        val genreId = request.data.toIntOrNull() ?: 1
        
        val filtered = channels.filter { it.genreID == genreId }
        
        val home = filtered.map { channel ->
            newMovieSearchResponse(
                channel.displayName,
                "$mainUrl/channel/${channel.streamName}|${channel.id}|${channel.displayName}",
                TvType.Live
            ) {
                this.posterUrl = channel.logoUrl
            }
        }
        
        return newHomePageResponse(request.name, home)
    }
    
    override suspend fun search(query: String): List<SearchResponse> {
        val channels = fetchChannels()
        
        val results = channels.filter { 
            it.displayName.contains(query, ignoreCase = true) ||
            it.name.contains(query, ignoreCase = true)
        }
        
        return results.map { channel ->
            newMovieSearchResponse(
                channel.displayName,
                "$mainUrl/channel/${channel.streamName}|${channel.id}|${channel.displayName}",
                TvType.Live
            ) {
                this.posterUrl = channel.logoUrl
            }
        }
    }
    
    override suspend fun load(url: String): LoadResponse {
        // URL format: mainUrl/channel/StreamName|ID|DisplayName
        val parts = url.substringAfterLast("/channel/").split("|")
        val streamName = parts.getOrNull(0) ?: ""
        val channelId = parts.getOrNull(1) ?: ""
        val displayName = parts.getOrNull(2) ?: streamName
        
        val logoUrl = if (channelId.isNotEmpty()) {
            "${logoBaseUrl}${channelId}.png"
        } else {
            ""
        }
        
        return newMovieLoadResponse(
            displayName,
            url,
            TvType.Live,
            streamName // Pass streamName as data for loadLinks
        ) {
            this.posterUrl = logoUrl
            this.plot = "Live stream from GLWiz"
        }
    }
    
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            // data is the streamName (channel internal name)
            val channelName = data.substringBefore("|")
            
            // Fetch stream URL via AJAX
            val streamUrl = getStreamUrl(channelName)
            
            if (streamUrl != null) {
                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = "$name - Live",
                        url = streamUrl
                    ).apply {
                        this.quality = Qualities.Unknown.value
                        this.referer = "$playerBaseUrl/p2.html"
                        this.headers = mapOf(
                            "Referer" to "$playerBaseUrl/p2.html",
                            "Origin" to mainUrl,
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                        )
                    }
                )
                return true
            }
            
            return false
        } catch (e: Exception) {
            return false
        }
    }
    
    /**
     * Fetch HLS stream URL from GLWiz AJAX endpoint
     */
    private suspend fun getStreamUrl(channelName: String): String? {
        try {
            // First, initialize session
            app.get("$playerBaseUrl/Player.aspx").text
            
            val url = "$ajaxUrl?action=getStreamURL&ClusterName=zixi-glwiz-mobile&RecType=4&itemName=$channelName&ScreenMode=0"
            
            val response = app.get(
                url,
                headers = mapOf(
                    "Referer" to "$playerBaseUrl/p2.html",
                    "X-Requested-With" to "XMLHttpRequest"
                )
            ).text
            
            // Response format: {"resp":"https://GLWizHHLS20.glwiz.com:443/Channel.m3u8?user=...&session=..."}
            // Extract the URL from the "resp" field
            val respRegex = Regex(""""resp"\s*:\s*"([^"]+)"""")
            val match = respRegex.find(response)
            
            if (match != null) {
                // Unescape JSON escaped characters by chaining replace calls
                val streamUrl = match.groupValues[1]
                    .replace("\\u0026", "&")
                    .replace("\\/", "/")
                    .replace("\\\"", "\"")
                return streamUrl
            }
            
            return null
        } catch (e: Exception) {
            return null
        }
    }
}
