package com.likdev256

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element

/**
 * IranWiz Provider - Persian Live TV from GLWiz
 * 
 * Stream extraction pattern:
 * 1. Initialize session by loading Player.aspx (gets ASP.NET_SessionId cookie)
 * 2. Call Ajax.aspx?action=getStreamURL&itemName=CHANNEL
 * 3. Response contains HLS m3u8 URL with auth tokens
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
    
    // Categories discovered from browser (genreID -> name)
    private val categories = mapOf(
        89 to "📰 News (خبری)",
        84 to "⚽ Sports (ورزشی)",
        10 to "🎵 Music (موسیقی)",
        4 to "👶 Kids (کودکان)",
        2 to "📺 IRIB (سیمای ایران)",
        19 to "🎬 Series & Movies (فیلم و سریال)",
        85 to "🕌 Religious (مذهبی)",
        3 to "🌍 Other Languages (زبانهای دیگر)"
    )
    
    // Known channels with their internal names (for testing)
    private val knownChannels = listOf(
        Channel("IranInternational", "Iran International", 89, "https://www.glwiz.com/menu/IranInternational.png"),
        Channel("Manoto", "Manoto", 89, "https://www.glwiz.com/menu/Manoto.png"),
        Channel("BBCPersian", "BBC Persian", 89, "https://www.glwiz.com/menu/BBCPersian.png"),
        Channel("IraneFarda", "Iran-e Farda", 89, "https://www.glwiz.com/menu/IraneFarda.png"),
        Channel("VOA", "VOA", 89, "https://www.glwiz.com/menu/VOA.png"),
        Channel("GEM", "GEM TV", 19, "https://www.glwiz.com/menu/GEM.png"),
        Channel("PMC", "PMC", 10, "https://www.glwiz.com/menu/PMC.png"),
        Channel("IRIB1", "IRIB 1", 2, "https://www.glwiz.com/menu/IRIB1.png"),
        Channel("IRIB2", "IRIB 2", 2, "https://www.glwiz.com/menu/IRIB2.png"),
        Channel("IRIB3", "IRIB 3", 2, "https://www.glwiz.com/menu/IRIB3.png"),
        Channel("TapeshAmerica", "Tapesh America", 89, "https://www.glwiz.com/menu/TapeshAmerica.png"),
        Channel("NahadeAzadi", "Nahade Azadi", 89, "https://www.glwiz.com/menu/NahadeAzadi.png"),
        Channel("EuronewsFarsi", "Euronews Farsi", 89, "https://www.glwiz.com/menu/EuronewsFarsi.png"),
    )
    
    data class Channel(
        val internalName: String,
        val displayName: String,
        val genreId: Int,
        val logoUrl: String
    )
    
    // Build main page from categories
    override val mainPage = mainPageOf(
        "$mainUrl/livetv/news" to "📰 News (خبری)",
        "$mainUrl/livetv/sports" to "⚽ Sports (ورزشی)",
        "$mainUrl/livetv/music" to "🎵 Music (موسیقی)",
        "$mainUrl/livetv/kids" to "👶 Kids (کودکان)",
        "$mainUrl/livetv/irib" to "📺 IRIB (سیمای ایران)",
        "$mainUrl/livetv/movies" to "🎬 Series & Movies",
    )
    
    // Initialize session when needed
    private suspend fun initSession() {
        try {
            // Load player page to get session cookie
            app.get("$playerBaseUrl/Player.aspx").text
        } catch (e: Exception) {
            // Session init failed, will try anyway
        }
    }
    
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // Initialize session first (for cookie)
        if (page == 1) {
            initSession()
        }
        
        // Filter channels by category based on the request URL
        val categoryName = request.name
        val genreId = when {
            categoryName.contains("News") -> 89
            categoryName.contains("Sports") -> 84
            categoryName.contains("Music") -> 10
            categoryName.contains("Kids") -> 4
            categoryName.contains("IRIB") -> 2
            categoryName.contains("Movies") || categoryName.contains("Series") -> 19
            else -> null
        }
        
        val channelsToShow = if (genreId != null) {
            knownChannels.filter { it.genreId == genreId }
        } else {
            knownChannels
        }
        
        val home = channelsToShow.map { channel ->
            newMovieSearchResponse(
                channel.displayName,
                "$mainUrl/channel/${channel.internalName}",
                TvType.Live
            ) {
                this.posterUrl = channel.logoUrl
            }
        }
        
        return newHomePageResponse(request.name, home)
    }
    
    override suspend fun search(query: String): List<SearchResponse> {
        // Search through known channels
        val results = knownChannels.filter { 
            it.displayName.contains(query, ignoreCase = true) ||
            it.internalName.contains(query, ignoreCase = true)
        }
        
        return results.map { channel ->
            newMovieSearchResponse(
                channel.displayName,
                "$mainUrl/channel/${channel.internalName}",
                TvType.Live
            ) {
                this.posterUrl = channel.logoUrl
            }
        }
    }
    
    override suspend fun load(url: String): LoadResponse {
        // Extract channel name from URL: /channel/ChannelName
        val channelName = url.substringAfterLast("/channel/").substringBefore("?")
        
        // Find channel info
        val channel = knownChannels.find { it.internalName.equals(channelName, ignoreCase = true) }
        val displayName = channel?.displayName ?: channelName
        val posterUrl = channel?.logoUrl ?: "$mainUrl/menu/$channelName.png"
        
        return newMovieLoadResponse(
            displayName,
            url,
            TvType.Live,
            url // Pass the URL as data for loadLinks
        ) {
            this.posterUrl = posterUrl
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
            // Extract channel name from data URL
            val channelName = data.substringAfterLast("/channel/").substringBefore("?")
            
            // Initialize session
            initSession()
            
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
            val url = "$ajaxUrl?action=getStreamURL&ClusterName=zixi-glwiz-mobile&RecType=4&itemName=$channelName&ScreenMode=0"
            
            val response = app.get(
                url,
                headers = mapOf(
                    "Referer" to "$playerBaseUrl/p2.html",
                    "X-Requested-With" to "XMLHttpRequest"
                )
            ).text
            
            // Extract m3u8 URL from response
            // Response format: {"resp":"https://GLWizHHLS20.glwiz.com:443/Channel.m3u8?user=...&session=..."}
            val m3u8Regex = Regex("(https?://[^\"'\\s]+\\.m3u8[^\"'\\s]*)")
            val match = m3u8Regex.find(response)
            
            if (match != null) {
                // Unescape any escaped characters
                return match.groupValues[1].replace("\\u0026", "&")
            }
            
            return null
        } catch (e: Exception) {
            return null
        }
    }
}
